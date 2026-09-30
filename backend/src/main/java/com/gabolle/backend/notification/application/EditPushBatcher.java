package com.gabolle.backend.notification.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gabolle.backend.itinerary.domain.ItineraryVersion;

/**
 * 연달아 바꾼 일정 알림을 한 통으로 모은다 — S15P21E201-1880 (UI 캔버스 ⑰-2).
 *
 * <p>🔴 <b>왜 서버에서 모으나.</b> 동행이 순서를 바꾸고, 두 곳을 빼고, 한 곳을 고정하면 편집마다 한 통씩 다섯 통이 잠금화면을
 * 덮었다. 폰이 알아서 묶어 주길 바랄 수 없다 — Expo 푸시에는 이미 뜬 알림을 새 알림으로 바꿔 끼우는 자리(APNs collapse-id ·
 * FCM tag)가 없다. 그래서 보내기 전에 모은다.
 *
 * <p><b>모으는 규칙.</b> 같은 사람이 같은 일정을 고친 것끼리. 마지막 편집 뒤 {@code quiet} 동안 조용하면 보낸다. 계속 고쳐도
 * 첫 편집부터 {@code max} 가 지나면 보낸다 — 한 시간 내내 고치는 사람 때문에 알림이 한 시간 늦으면 안 된다.
 *
 * <p>🟡 <b>알려진 한계 — 모으는 동안은 메모리에만 있다.</b> 서버가 그 1~5분 사이에 다시 뜨면 그 묶음은 안 나간다. 알림은
 * 원래 「안 간 것이 늦게 간 것보다 낫다」로 다뤄 왔다({@code ExpoPushSender} 는 실패해도 다시 보내지 않는다). 서버가 여러 대로
 * 늘면 같은 사람의 편집이 두 대로 나뉘어 두 통이 될 수 있다 — 지금은 한 대다.
 *
 * <p>{@code scheduler} 가 없으면 모으지 않고 바로 보낸다 — 시험과 예전 동작이 그대로다.
 */
public class EditPushBatcher implements AutoCloseable {

	private static final Logger log = LoggerFactory.getLogger(EditPushBatcher.class);

	/** 같은 사람 · 같은 일정. 작성자를 모르면(탈퇴) {@code null} 끼리 모인다. */
	public record Key(String itineraryId, String actorUserId) {
	}

	private static final class Batch {

		private final Instant first;

		private final List<ItineraryVersion.Operation> operations = new ArrayList<>();

		private ScheduledFuture<?> timer;

		private Batch(Instant first) {
			this.first = first;
		}
	}

	private final ScheduledExecutorService scheduler;

	private final Duration quiet;

	private final Duration max;

	private final Clock clock;

	private final Map<Key, Batch> pending = new HashMap<>();

	/** 모으지 않고 바로 보낸다. */
	public static EditPushBatcher immediate() {
		return new EditPushBatcher(null, Duration.ZERO, Duration.ZERO, Clock.systemUTC());
	}

	public EditPushBatcher(ScheduledExecutorService scheduler, Duration quiet, Duration max, Clock clock) {
		this.scheduler = scheduler;
		this.quiet = quiet;
		this.max = max;
		this.clock = clock;
	}

	/**
	 * 편집 한 건을 넣는다. 묶음이 다 차면 {@code flush} 가 모인 편집 종류들(일어난 순서)과 함께 불린다 — 모으지 않으면 그 자리에서.
	 */
	public void add(Key key, ItineraryVersion.Operation operation,
			BiConsumer<Key, List<ItineraryVersion.Operation>> flush) {
		if (this.scheduler == null) {
			flush.accept(key, List.of(operation));
			return;
		}
		synchronized (this.pending) {
			Instant now = this.clock.instant();
			Batch batch = this.pending.computeIfAbsent(key, (ignored) -> new Batch(now));
			batch.operations.add(operation);
			if (batch.timer != null) {
				batch.timer.cancel(false);
			}
			// 조용해지기를 기다리되, 첫 편집부터 max 를 넘기지 않는다.
			Duration untilCap = Duration.between(now, batch.first.plus(this.max));
			Duration wait = untilCap.compareTo(this.quiet) < 0 ? untilCap : this.quiet;
			long delayMillis = Math.max(0, wait.toMillis());
			batch.timer = this.scheduler.schedule(() -> fire(key, flush), delayMillis, TimeUnit.MILLISECONDS);
		}
	}

	/**
	 * 묶음을 꺼내 보낸다. 꺼낸 뒤에 들어온 편집은 새 묶음이 된다 — 「보낸 뒤의 편집은 다음 알림」이 뜻한 동작이다.
	 * 새 묶음은 적어도 {@code quiet} 뒤에 나가므로 지금 보내는 것과 순서가 뒤집히지 않는다(AI 리뷰 !1923).
	 */
	private void fire(Key key, BiConsumer<Key, List<ItineraryVersion.Operation>> flush) {
		List<ItineraryVersion.Operation> operations;
		synchronized (this.pending) {
			Batch batch = this.pending.remove(key);
			if (batch == null) {
				return;
			}
			operations = List.copyOf(batch.operations);
		}
		try {
			flush.accept(key, operations);
		}
		catch (RuntimeException exception) {
			// 예약 실행자 안에서 던지면 아무도 못 본다 — 여기서 남긴다.
			log.error("모아 둔 일정 알림을 보내지 못했습니다. itineraryId={} 편집 {}건", key.itineraryId(), operations.size(),
					exception);
		}
	}

	/** 서버가 꺼질 때 예약 실행자를 닫는다 — 아직 모으던 묶음은 보내지 않는다(알림은 다시 보내지 않는 규칙과 같다). */
	@Override
	public void close() {
		if (this.scheduler != null) {
			this.scheduler.shutdownNow();
		}
	}

	/** 지금 모으고 있는 묶음 수 — 시험용. */
	int pendingCount() {
		synchronized (this.pending) {
			return this.pending.size();
		}
	}
}
