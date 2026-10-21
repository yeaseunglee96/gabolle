package com.gabolle.backend.itinerary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.itinerary.domain.ItineraryContent;
import com.gabolle.backend.itinerary.domain.ItineraryItem;
import com.gabolle.backend.itinerary.domain.ItineraryRevision;

/**
 * 일정 조정(S15P21E201-2017) — 「순서 고정」·「시각 고정」 두 고정과, 고른 자리에 장소 끼워 넣기, 시각 다시 매기기.
 * DB 를 안 쓴다. 저장까지는 {@link ItineraryAdjustIntegrationTest} 가 본다.
 */
class ItineraryAdjustRevisionTest {

	private static final Instant NOW = Instant.parse("2026-10-09T00:00:00Z");

	private static final LocalDate DAY0 = LocalDate.of(2026, 10, 10);

	private static ItineraryItem item(String key, int sequence, LocalTime start, boolean locked) {
		return new ItineraryItem("id_" + key, "ver_base", key, 0, DAY0, sequence, "place_" + key, start,
				start.plusHours(1), 60, locked, null, ItineraryItem.DataStatus.ESTIMATED, List.of(), List.of(), null,
				NOW);
	}

	private static ItineraryContent base() {
		return new ItineraryContent(null,
				List.of(item("A", 1, LocalTime.of(9, 0), false), item("B", 2, LocalTime.of(11, 0), true),
						item("C", 3, LocalTime.of(13, 0), false)),
				List.of(), List.of());
	}

	private static List<ItineraryItem> day(ItineraryRevision.Draft draft) {
		List<ItineraryItem> items = new ArrayList<>(draft.items());
		items.sort(Comparator.comparingInt(ItineraryItem::sequence));
		return items;
	}

	@Test
	@DisplayName("🔴 예전 고정(locked=true, 방식 없음)은 「순서 고정」으로 읽힌다 — 옛 판의 고정이 뜻을 바꾸지 않는다")
	void legacyLockReadsAsOrderLock() {
		assertThat(item("B", 2, LocalTime.of(11, 0), true).lockMode()).isEqualTo(ItineraryItem.LockMode.ORDER);
		assertThat(item("A", 1, LocalTime.of(9, 0), false).lockMode()).isNull();
	}

	@Test
	@DisplayName("시각 고정으로 걸면 판을 복사해도(되돌리기·다른 편집) 시각 고정이 남는다")
	void timeLockSurvivesCopies() {
		ItineraryRevision.Draft locked = ItineraryRevision.setLocked(base(), "v2", "A", true,
				ItineraryItem.LockMode.TIME, NOW);
		ItineraryItem a = day(locked).get(0);
		assertThat(a.locked()).isTrue();
		assertThat(a.lockMode()).isEqualTo(ItineraryItem.LockMode.TIME);

		ItineraryRevision.Draft copied = ItineraryRevision.copyOf(
				new ItineraryContent(null, locked.items(), locked.legs(), locked.exclusions()), "v3", NOW);
		assertThat(day(copied).get(0).lockMode()).isEqualTo(ItineraryItem.LockMode.TIME);
	}

	@Test
	@DisplayName("고정을 풀면 방식도 지운다")
	void unlockClearsMode() {
		ItineraryRevision.Draft unlocked = ItineraryRevision.setLocked(base(), "v2", "B", false, null, NOW);
		ItineraryItem b = day(unlocked).get(1);
		assertThat(b.locked()).isFalse();
		assertThat(b.lockMode()).isNull();
	}

	@Test
	@DisplayName("방식이 있는데 고정이 아니면 항목이 스스로를 거부한다")
	void modeWithoutLockIsRejected() {
		assertThatThrownBy(() -> new ItineraryItem("id", "v", "K", 0, DAY0, 1, "p", null, null, null, false, null,
				ItineraryItem.DataStatus.UNKNOWN, List.of(), List.of(), null, NOW, ItineraryItem.LockMode.TIME))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("고른 자리(2번째)에 끼워 넣으면 그 뒤 곳들의 순번이 하나씩 밀린다")
	void insertAtPositionShiftsFollowingSequences() {
		ItineraryRevision.Draft draft = ItineraryRevision.withAddedItem(base(), "v2", "place_N", 0, DAY0, 2, NOW);

		List<ItineraryItem> items = day(draft);
		assertThat(items).extracting(ItineraryItem::placeId)
				.containsExactly("place_A", "place_N", "place_B", "place_C");
		assertThat(items).extracting(ItineraryItem::sequence).containsExactly(1, 2, 3, 4);
		assertThat(items.get(1).startTime()).isNull();
	}

	@Test
	@DisplayName("자리를 그날 곳 수보다 크게 주면 맨 끝에 붙는다 — 범위 밖 숫자로 실패시키지 않는다")
	void positionBeyondEndAppends() {
		ItineraryRevision.Draft draft = ItineraryRevision.withAddedItem(base(), "v2", "place_N", 0, DAY0, 99, NOW);
		assertThat(day(draft)).extracting(ItineraryItem::placeId).last().isEqualTo("place_N");
	}

	@Test
	@DisplayName("다시 매긴 시각과 머무는 시간이 들어가고, 시각을 모르던 곳은 어림(ESTIMATED)이 된다")
	void retimedDayGetsNewTimesAndStay() {
		ItineraryRevision.Draft added = ItineraryRevision.withAddedItem(base(), "v2", "place_N", 0, DAY0, 4, NOW);
		ItineraryItem n = day(added).get(3);
		ItineraryRevision.Draft retimed = ItineraryRevision.withRetimedDay(
				new ItineraryContent(null, added.items(), added.legs(), added.exclusions()), "v2", 0,
				Map.of("A", LocalTime.of(9, 0), n.itemKey(), LocalTime.of(14, 10)),
				Map.of("A", LocalTime.of(9, 30), n.itemKey(), LocalTime.of(15, 10)), NOW);

		List<ItineraryItem> items = day(retimed);
		assertThat(items.get(0).stayMinutes()).isEqualTo(30);
		assertThat(items.get(3).startTime()).isEqualTo(LocalTime.of(14, 10));
		assertThat(items.get(3).stayMinutes()).isEqualTo(60);
		assertThat(items.get(3).dataStatus()).isEqualTo(ItineraryItem.DataStatus.ESTIMATED);
		// 지도에 없는 곳은 그대로다.
		assertThat(items.get(1).startTime()).isEqualTo(LocalTime.of(11, 0));
		assertThat(items.get(1).lockMode()).isEqualTo(ItineraryItem.LockMode.ORDER);
	}
}
