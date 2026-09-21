package com.gabolle.backend.itinerary.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.itinerary.domain.ItineraryContent;
import com.gabolle.backend.itinerary.domain.ItineraryItem;
import com.gabolle.backend.itinerary.domain.ItineraryItemActual;
import com.gabolle.backend.itinerary.domain.ItineraryItemActualRepository;
import com.gabolle.backend.itinerary.domain.ItineraryRepository;
import com.gabolle.backend.itinerary.domain.ItineraryRun;
import com.gabolle.backend.itinerary.domain.ItineraryRunRepository;
import com.gabolle.backend.itinerary.domain.ItineraryStopEvent;
import com.gabolle.backend.itinerary.presentation.ItineraryQueryController;

/**
 * 일정 진행 — 지금 어느 단계인가.
 *
 * <p>도착 시각은 여기서 새로 만들지 않는다. {@link ItineraryActualTimeService} 가 쓰는 표에
 * 적고, 이 서비스는 그 위에 「자동인가 손인가」와 「몇 번째를 향하고 있나」만 얹는다.
 *
 * <p>시각은 {@link Clock} 에서만 읽는다.
 */
@Service
@Profile({ "db", "dev" })
@ConditionalOnBean(ItineraryAccess.class)
public class ItineraryRunService {

	private final ItineraryAccess access;

	private final ItineraryRepository itineraries;

	private final ItineraryRunRepository runs;

	private final ItineraryItemActualRepository actuals;

	private final Clock clock;

	public ItineraryRunService(ItineraryAccess access, ItineraryRepository itineraries, ItineraryRunRepository runs,
			ItineraryItemActualRepository actuals, Clock clock) {
		this.access = access;
		this.itineraries = itineraries;
		this.runs = runs;
		this.actuals = actuals;
		this.clock = clock;
	}

	/** 진행 상태와 정차지마다 일어난 일. 화면이 한 번에 읽는다. */
	public record View(ItineraryRun run, List<Stop> stops) {
	}

	/**
	 * 정차지 하나의 지금. {@code arrivedAt} 과 {@code skipped} 는 한 칸으로 합치지 않는다 —
	 * 건너뛴 곳은 다녀온 곳이 아니다.
	 *
	 * @param arrivedHow {@code auto} 또는 {@code manual}. 안 갔으면 {@code null}
	 */
	public record Stop(String itemKey, int index, Instant arrivedAt, String arrivedHow, boolean skipped) {
	}

	@Transactional(readOnly = true)
	public View get(String itineraryId, String userId) {
		ItineraryAccess.Access granted = this.access.requireMember(itineraryId, userId);
		return view(itineraryId, granted.itinerary().tripId(), stopKeys(itineraryId));
	}

	/** 출발 — 위치 추적이 시작된다. */
	@Transactional
	public View start(String itineraryId, String userId) {
		return apply(itineraryId, userId, ItineraryStopEvent.Type.START, null, null);
	}

	/** 중지 — 추적과 자동 도착 기록을 멈춘다. 다녀온 것은 그대로 남는다. */
	@Transactional
	public View pause(String itineraryId, String userId) {
		return apply(itineraryId, userId, ItineraryStopEvent.Type.PAUSE, null, null);
	}

	/**
	 * 도착했다.
	 *
	 * @param how {@code auto} 면 GPS 가 알아챈 것, {@code manual} 이면 사람이 찍은 것.
	 *     둘을 가르는 것은 나중에 자동 판정이 얼마나 맞았는지를 재기 위해서다
	 */
	@Transactional
	public View arrive(String itineraryId, String userId, String itemKey, String how) {
		ItineraryStopEvent.Type type = "manual".equalsIgnoreCase(how)
				? ItineraryStopEvent.Type.ARRIVE_MANUAL
				: ItineraryStopEvent.Type.ARRIVE_AUTO;
		return apply(itineraryId, userId, type, itemKey, Instant.now(this.clock));
	}

	/** 건너뛴다 — 이 정차지를 빼고 다음으로. */
	@Transactional
	public View skip(String itineraryId, String userId, String itemKey) {
		return apply(itineraryId, userId, ItineraryStopEvent.Type.SKIP, itemKey, null);
	}

	private View apply(String itineraryId, String userId, ItineraryStopEvent.Type type, String itemKey,
			Instant arrivedAt) {
		// 편집 권한을 요구한다. 보기 전용으로 초대된 사람은 진행 기록을 남길 수 없다.
		ItineraryAccess.Access granted = this.access.requireEditor(itineraryId, userId);
		String tripId = granted.itinerary().tripId();
		List<String> keys = stopKeys(itineraryId);
		Instant now = Instant.now(this.clock);

		if (type.needsStop() && !keys.contains(itemKey)) {
			throw new UnknownStopException(itineraryId, itemKey);
		}

		ItineraryRun run = this.runs.find(itineraryId)
				.orElseGet(() -> ItineraryRun.planned(itineraryId, tripId, now));

		// 달리는 중이 아니면 정차지 사건을 안 받는다.
		if (type.settlesStop() && run.status() != ItineraryRun.Status.RUNNING) {
			throw new NotRunningException(itineraryId, run.status());
		}

		ItineraryRun next = switch (type) {
			case START -> run.start(now);
			case PAUSE -> run.pause(now);
			default -> run;
		};

		if (type.settlesStop()) {
			// 같은 정차지를 두 번 찍어도 한 번만 센다. 재시도와 늦게 온 신호가 정상 경로라,
			// 막지 않으면 「몇 번째」가 실제보다 앞서 간다.
			Map<String, Stop> before = stopsByKey(itineraryId, keys);
			Stop already = before.get(itemKey);
			if (already != null && (already.arrivedAt() != null || already.skipped())) {
				return view(itineraryId, tripId, keys);
			}
			record(itineraryId, itemKey, type, now, arrivedAt, userId);
			next = next.advance(settledIndexes(itineraryId, keys), keys.size(), now);
		}
		else {
			appendEvent(itineraryId, null, type, now, userId);
		}

		this.runs.upsert(next);
		return view(itineraryId, tripId, keys);
	}

	private void record(String itineraryId, String itemKey, ItineraryStopEvent.Type type, Instant now,
			Instant arrivedAt, String userId) {
		appendEvent(itineraryId, itemKey, type, now, userId);
		if (!type.isArrival()) {
			return;
		}
		// 도착 시각은 따로 들지 않고 이미 있는 표에 적는다.
		this.actuals.upsert(new ItineraryItemActual(itineraryId, itemKey, arrivedAt == null ? now : arrivedAt, null,
				userId, now));
	}

	private void appendEvent(String itineraryId, String itemKey, ItineraryStopEvent.Type type, Instant now,
			String userId) {
		this.runs.append(new ItineraryStopEvent(UUID.randomUUID().toString(), itineraryId, itemKey, type, now, userId,
				now));
	}

	/**
	 * 그 일정의 정차지를 순서대로. 최신 판을 읽는다 — 진행 상태는 판 체인 밖에 있어서
	 * 일정을 고친 뒤에도 「몇 번째」는 새 판의 순서로 읽혀야 한다.
	 */
	private List<String> stopKeys(String itineraryId) {
		var itinerary = this.itineraries.findById(itineraryId)
				.orElseThrow(() -> new ItineraryQueryController.ItineraryNotFoundException(itineraryId));
		ItineraryContent content = this.itineraries.findContent(itineraryId, itinerary.latestVersion())
				.orElseThrow(() -> new ItineraryQueryController.ItineraryNotFoundException(itineraryId));
		List<ItineraryItem> items = new ArrayList<>(content.items());
		items.sort(Comparator.comparingInt(ItineraryItem::dayIndex).thenComparingInt(ItineraryItem::sequence));
		return items.stream().map(ItineraryItem::itemKey).toList();
	}

	private Map<String, Stop> stopsByKey(String itineraryId, List<String> keys) {
		Map<String, Instant> arrived = new LinkedHashMap<>();
		for (ItineraryItemActual actual : this.actuals.findByItineraryId(itineraryId)) {
			if (actual.arrivedAt() != null) {
				arrived.put(actual.itemKey(), actual.arrivedAt());
			}
		}
		Map<String, String> how = new LinkedHashMap<>();
		Map<String, Boolean> skipped = new LinkedHashMap<>();
		for (ItineraryStopEvent event : this.runs.findEvents(itineraryId)) {
			if (event.itemKey() == null) {
				continue;
			}
			if (event.type() == ItineraryStopEvent.Type.SKIP) {
				skipped.put(event.itemKey(), true);
			}
			else if (event.type().isArrival()) {
				how.put(event.itemKey(), event.type() == ItineraryStopEvent.Type.ARRIVE_MANUAL ? "manual" : "auto");
			}
		}
		Map<String, Stop> stops = new LinkedHashMap<>();
		for (int i = 0; i < keys.size(); i++) {
			String key = keys.get(i);
			stops.put(key, new Stop(key, i, arrived.get(key), how.get(key), skipped.getOrDefault(key, false)));
		}
		return stops;
	}

	private List<Integer> settledIndexes(String itineraryId, List<String> keys) {
		List<Integer> settled = new ArrayList<>();
		for (Stop stop : stopsByKey(itineraryId, keys).values()) {
			if (stop.arrivedAt() != null || stop.skipped()) {
				settled.add(stop.index());
			}
		}
		return settled;
	}

	private View view(String itineraryId, String tripId, List<String> keys) {
		ItineraryRun run = this.runs.find(itineraryId)
				.orElseGet(() -> ItineraryRun.planned(itineraryId, tripId, Instant.now(this.clock)));
		return new View(run, List.copyOf(stopsByKey(itineraryId, keys).values()));
	}

	/** 그 일정에 없는 정차지를 가리켰다 — 400. */
	public static class UnknownStopException extends RuntimeException {

		public UnknownStopException(String itineraryId, String itemKey) {
			super("이 일정에 없는 정차지예요: " + itemKey + " (" + itineraryId + ")");
		}
	}

	/** 달리고 있지 않은데 도착·건너뛰기를 보냈다 — 409. */
	public static class NotRunningException extends RuntimeException {

		private final ItineraryRun.Status status;

		public NotRunningException(String itineraryId, ItineraryRun.Status status) {
			super("출발한 뒤에 기록할 수 있어요. 지금은 " + status + " 예요. (" + itineraryId + ")");
			this.status = status;
		}

		public ItineraryRun.Status status() {
			return this.status;
		}
	}
}
