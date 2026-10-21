package com.gabolle.backend.notification;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.itinerary.application.ActorNames;
import com.gabolle.backend.itinerary.domain.Itinerary;
import com.gabolle.backend.itinerary.domain.ItineraryChangedByMember;
import com.gabolle.backend.itinerary.domain.ItineraryRepository;
import com.gabolle.backend.itinerary.domain.ItineraryVersion.Operation;
import com.gabolle.backend.notification.application.EditPushBatcher;
import com.gabolle.backend.notification.application.PushCopy;
import com.gabolle.backend.notification.application.PushMessage;
import com.gabolle.backend.notification.application.PushSender;
import com.gabolle.backend.notification.application.PushTokenService;
import com.gabolle.backend.notification.application.TripPushNotifier;
import com.gabolle.backend.trip.domain.Trip;
import com.gabolle.backend.trip.domain.TripMember;
import com.gabolle.backend.trip.domain.TripRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 연달아 바꾼 일정 알림을 한 통으로 — S15P21E201-1880 (UI 캔버스 ⑰-2).
 *
 * <p>진짜 예약 실행자로 돈다 — 기다리는 시간을 수십~수백 밀리초로 줄여서. 「모았다가 한 번」은 시계가 흘러야 보이는 일이라
 * 가짜 시계로는 예약이 안 풀린다.
 */
class EditPushBatchingTest {

	private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

	@AfterEach
	void tearDown() {
		this.scheduler.shutdownNow();
	}

	private EditPushBatcher batcher(long quietMillis, long maxMillis) {
		return new EditPushBatcher(this.scheduler, Duration.ofMillis(quietMillis), Duration.ofMillis(maxMillis),
				Clock.systemUTC());
	}

	private record Flushed(EditPushBatcher.Key key, List<Operation> operations) {
	}

	@Test
	@DisplayName("🔴 연달아 온 편집 다섯은 한 번에 — 일어난 순서 그대로")
	void burstBecomesOne() {
		List<Flushed> flushed = new CopyOnWriteArrayList<>();
		EditPushBatcher batcher = batcher(150, 5_000);
		EditPushBatcher.Key key = new EditPushBatcher.Key("itn_1", "usr_mate");
		for (Operation op : List.of(Operation.REMOVE_ITEM, Operation.REORDER, Operation.REMOVE_ITEM, Operation.LOCK_ITEM,
				Operation.REORDER)) {
			batcher.add(key, op, (k, ops) -> flushed.add(new Flushed(k, ops)));
		}

		await().atMost(Duration.ofSeconds(3)).until(() -> flushed.size() == 1);
		assertThat(flushed.get(0).operations()).containsExactly(Operation.REMOVE_ITEM, Operation.REORDER,
				Operation.REMOVE_ITEM, Operation.LOCK_ITEM, Operation.REORDER);
	}

	@Test
	@DisplayName("🔴 계속 고쳐도 최대 시간이 지나면 보낸다 — 알림이 한없이 늦어지지 않게")
	void capFlushesEvenWhileStillEditing() throws InterruptedException {
		List<Flushed> flushed = new CopyOnWriteArrayList<>();
		EditPushBatcher batcher = batcher(300, 400);
		EditPushBatcher.Key key = new EditPushBatcher.Key("itn_1", "usr_mate");
		Instant start = Instant.now();
		// 조용해질 틈(300ms) 없이 100ms 마다 고친다 — 1초 동안.
		while (Duration.between(start, Instant.now()).toMillis() < 1_000) {
			batcher.add(key, Operation.REORDER, (k, ops) -> flushed.add(new Flushed(k, ops)));
			Thread.sleep(100);
		}
		await().atMost(Duration.ofSeconds(3)).until(() -> flushed.stream().mapToInt((f) -> f.operations().size()).sum() >= 9);
		assertThat(flushed.size()).as("최대 시간에 끊겨 두 번 이상 나간다").isGreaterThanOrEqualTo(2);
	}

	@Test
	@DisplayName("사람이 다르거나 일정이 다르면 따로 간다")
	void differentKeysDoNotMerge() {
		List<Flushed> flushed = new CopyOnWriteArrayList<>();
		EditPushBatcher batcher = batcher(100, 5_000);
		batcher.add(new EditPushBatcher.Key("itn_1", "usr_a"), Operation.REORDER, (k, ops) -> flushed.add(new Flushed(k, ops)));
		batcher.add(new EditPushBatcher.Key("itn_1", "usr_b"), Operation.REORDER, (k, ops) -> flushed.add(new Flushed(k, ops)));
		batcher.add(new EditPushBatcher.Key("itn_2", "usr_a"), Operation.REORDER, (k, ops) -> flushed.add(new Flushed(k, ops)));
		await().atMost(Duration.ofSeconds(3)).until(() -> flushed.size() == 3);
	}

	@Test
	@DisplayName("모으지 않는 장치는 그 자리에서 한 건씩 — 예전 동작")
	void immediateSendsRightAway() {
		List<Flushed> flushed = new ArrayList<>();
		EditPushBatcher.immediate().add(new EditPushBatcher.Key("itn_1", "usr_a"), Operation.REORDER,
				(k, ops) -> flushed.add(new Flushed(k, ops)));
		assertThat(flushed).hasSize(1);
	}

	@Test
	@DisplayName("🔴 묶음 문구 — 캔버스 ⑰-2 그대로, 종류는 늘 같은 차례로")
	void groupedCopy() {
		List<Operation> ops = List.of(Operation.REORDER, Operation.REMOVE_ITEM, Operation.LOCK_ITEM, Operation.REMOVE_ITEM,
				Operation.REORDER);
		assertThat(PushCopy.groupedEditTitle("박재현", 5, PushCopy.Lang.KO)).isEqualTo("박재현님이 일정을 5번 바꿨어요");
		assertThat(PushCopy.groupedEditBody("버터플라이케익 여행", ops, PushCopy.Lang.KO))
				.isEqualTo("버터플라이케익 여행 · 2곳 빼고 · 1곳 고정 · 순서 2번");
		assertThat(PushCopy.groupedEditTitle("Minji", 5, PushCopy.Lang.EN)).isEqualTo("Minji made 5 changes to the plan");
		assertThat(PushCopy.groupedEditBody("Busan trip", ops, PushCopy.Lang.JA)).isEqualTo("Busan trip · 2か所削除 · 1か所固定 · 順番変更2回");
		assertThat(PushCopy.groupedEditBody("Busan trip", ops, PushCopy.Lang.ZH_HANT)).isEqualTo("Busan trip · 移除2處 · 鎖定1處 · 調整順序2次");
		// 작성자를 모르면(탈퇴) 「누군가」를 지어내지 않는다.
		assertThat(PushCopy.groupedEditTitle(null, 3, PushCopy.Lang.KO)).isEqualTo("일정이 3번 바뀌었어요");
	}

	@Test
	@DisplayName("🔴 알리미 — 연달아 바꾸면 동행에게 한 통, 한 건이면 예전 문구")
	void notifierSendsOnePushForABurst() {
		ItineraryRepository itineraries = mock(ItineraryRepository.class);
		TripRepository trips = mock(TripRepository.class);
		ActorNames actorNames = mock(ActorNames.class);
		PushTokenService tokens = mock(PushTokenService.class);
		List<PushMessage> sent = new CopyOnWriteArrayList<>();
		PushSender sender = (List<String> t, PushMessage message) -> {
			sent.add(message);
			return List.of();
		};
		when(itineraries.findById("itn_1")).thenReturn(Optional.of(new Itinerary("itn_1", "trp_1", 5)));
		when(trips.findById("trp_1")).thenReturn(Optional.of(Trip.builder().tripId("trp_1").createdBy("usr_me")
				.ownerType(Trip.OwnerType.USER).startDate(LocalDate.of(2026, 10, 1)).finishDate(LocalDate.of(2026, 10, 3))
				.partySize(2).timezone("Asia/Seoul").title("부산 바다").createdAt(Instant.parse("2026-09-01T00:00:00Z")).build()));
		when(trips.findMembers("trp_1")).thenReturn(List.of(
				TripMember.owner("tm_1", "trp_1", "usr_me", Instant.parse("2026-09-01T00:00:00Z")),
				TripMember.invited("tm_2", "trp_1", "usr_mate", TripMember.Role.EDITOR, Instant.parse("2026-09-02T00:00:00Z"))));
		when(actorNames.resolve(anyCollection())).thenReturn(Map.of("usr_mate", "지훈"));
		when(tokens.tokensOf(anyCollection())).thenAnswer((call) -> ((Collection<?>) call.getArgument(0)).stream()
				.map((id) -> "tok:" + id).toList());

		TripPushNotifier notifier = new TripPushNotifier(itineraries, trips, actorNames, tokens, sender, batcher(150, 5_000));
		for (Operation op : List.of(Operation.REMOVE_ITEM, Operation.REMOVE_ITEM, Operation.LOCK_ITEM)) {
			notifier.onItineraryChanged(new ItineraryChangedByMember("itn_1", 6, op, "usr_mate"));
		}
		await().atMost(Duration.ofSeconds(3)).until(() -> sent.size() == 1);
		assertThat(sent.get(0).title()).isEqualTo("지훈님이 일정을 3번 바꿨어요");
		assertThat(sent.get(0).body()).isEqualTo("부산 바다 · 2곳 빼고 · 1곳 고정");

		notifier.onItineraryChanged(new ItineraryChangedByMember("itn_1", 7, Operation.REORDER, "usr_mate"));
		await().atMost(Duration.ofSeconds(3)).until(() -> sent.size() == 2);
		assertThat(sent.get(1).title()).isEqualTo("지훈님이 일정 순서를 바꿨어요");
	}
}
