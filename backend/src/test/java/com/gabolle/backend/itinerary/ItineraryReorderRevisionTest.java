package com.gabolle.backend.itinerary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.itinerary.domain.ItineraryContent;
import com.gabolle.backend.itinerary.domain.ItineraryItem;
import com.gabolle.backend.itinerary.domain.ItineraryLeg;
import com.gabolle.backend.itinerary.domain.ItineraryRevision;

/**
 * 하루 안의 방문 순서 바꾸기.
 *
 * <p>DB 를 안 쓴다. 여기서 재는 규칙이 표와 상관이 없기 때문이다. 저장까지 이어지는 것은
 * 통합 검사가 따로 본다.
 */
class ItineraryReorderRevisionTest {

	private static final Instant NOW = Instant.parse("2026-09-08T00:00:00Z");

	private static final LocalDate DAY0 = LocalDate.of(2026, 9, 10);

	private static ItineraryItem item(String key, int dayIndex, int sequence, LocalTime start, boolean locked) {
		return new ItineraryItem("id_" + key, "ver_base", key, dayIndex, DAY0.plusDays(dayIndex), sequence,
				"place_" + key, start, start == null ? null : start.plusHours(2), start == null ? null : 120,
				locked, null, ItineraryItem.DataStatus.ESTIMATED, List.of(), List.of(), null, NOW);
	}

	private static ItineraryLeg leg(int dayIndex, int sequence, String toKey) {
		return new ItineraryLeg("leg_" + dayIndex + "_" + sequence, "ver_base", dayIndex, sequence,
				null, "place_" + toKey, "WALK", 500, null, 500, null, null, NOW);
	}

	/** 0일차에 셋(A 09:00 · B 11:00 · C 13:00), 1일차에 하나. 구간은 양쪽 날에 있다. */
	private static ItineraryContent base() {
		return new ItineraryContent(null,
				List.of(item("A", 0, 1, LocalTime.of(9, 0), false),
						item("B", 0, 2, LocalTime.of(11, 0), false),
						item("C", 0, 3, LocalTime.of(13, 0), false),
						item("Z", 1, 1, LocalTime.of(9, 0), false)),
				List.of(leg(0, 1, "A"), leg(0, 2, "B"), leg(0, 3, "C"), leg(1, 1, "Z")),
				List.of());
	}

	private static List<ItineraryItem> dayOf(ItineraryRevision.Draft draft, int dayIndex) {
		List<ItineraryItem> items = new ArrayList<>(draft.items().stream()
				.filter((item) -> item.dayIndex() == dayIndex).toList());
		items.sort(Comparator.comparingInt(ItineraryItem::sequence));
		return items;
	}

	@Test
	@DisplayName("보낸 순서대로 다시 앉고 순번은 1부터 다시 매겨진다")
	void itemsTakeTheRequestedOrderAndSequencesAreRenumbered() {
		ItineraryRevision.Draft draft = ItineraryRevision.withReorderedDay(
				base(), "ver_new", 0, List.of("C", "A", "B"), NOW);

		assertThat(dayOf(draft, 0)).extracting(ItineraryItem::itemKey).containsExactly("C", "A", "B");
		assertThat(dayOf(draft, 0)).extracting(ItineraryItem::sequence).containsExactly(1, 2, 3);
	}

	@Test
	@DisplayName("🔴 시각표는 그대로고 자리만 바뀐다 — 순서를 바꿨는데 하루가 늦게 시작하면 안 된다")
	void theDayKeepsItsTimetableAndOnlyTheSeatingChanges() {
		ItineraryRevision.Draft draft = ItineraryRevision.withReorderedDay(
				base(), "ver_new", 0, List.of("C", "A", "B"), NOW);

		// 자리의 시각은 09:00 · 11:00 · 13:00 그대로다. C 가 첫 자리로 왔으니 09:00 이다.
		assertThat(dayOf(draft, 0)).extracting(ItineraryItem::startTime)
				.containsExactly(LocalTime.of(9, 0), LocalTime.of(11, 0), LocalTime.of(13, 0));
		assertThat(dayOf(draft, 0).get(0).itemKey()).isEqualTo("C");
	}

	@Test
	@DisplayName("다른 날은 하나도 안 건드린다")
	void otherDaysAreUntouched() {
		ItineraryRevision.Draft draft = ItineraryRevision.withReorderedDay(
				base(), "ver_new", 0, List.of("C", "A", "B"), NOW);

		assertThat(dayOf(draft, 1)).extracting(ItineraryItem::itemKey).containsExactly("Z");
		assertThat(draft.legs()).extracting(ItineraryLeg::dayIndex).containsExactly(1);
	}

	@Test
	@DisplayName("🔴 그날의 구간은 버린다 — 순서가 바뀌면 '이만큼 걸었다' 가 더 이상 참이 아니다")
	void legsOfTheReorderedDayAreDropped() {
		ItineraryRevision.Draft draft = ItineraryRevision.withReorderedDay(
				base(), "ver_new", 0, List.of("C", "A", "B"), NOW);

		assertThat(draft.legs()).as("0일차 구간이 남아 있으면 화면이 남의 거리를 붙여 그린다")
				.noneMatch((l) -> l.dayIndex() == 0);
	}

	@Test
	@DisplayName("🔴 하나라도 빠지면 거부한다 — 조용히 뒤에 붙이면 안 보낸 순서를 받게 된다")
	void aPartialOrderIsRejected() {
		assertThatThrownBy(() -> ItineraryRevision.withReorderedDay(base(), "ver_new", 0, List.of("C", "A"), NOW))
				.isInstanceOf(ItineraryRevision.DayOrderMismatchException.class)
				.hasMessageContaining("빠진 항목 1개");
	}

	@Test
	@DisplayName("같은 항목이 두 번 들어오면 거부한다")
	void duplicatesAreRejected() {
		assertThatThrownBy(() -> ItineraryRevision.withReorderedDay(
				base(), "ver_new", 0, List.of("A", "A", "B"), NOW))
				.isInstanceOf(ItineraryRevision.DayOrderMismatchException.class)
				.hasMessageContaining("두 번");
	}

	@Test
	@DisplayName("그날에 없는 항목이 섞이면 거부한다 — 다른 날 항목을 끌어와도 마찬가지다")
	void itemsFromAnotherDayAreRejected() {
		assertThatThrownBy(() -> ItineraryRevision.withReorderedDay(
				base(), "ver_new", 0, List.of("A", "B", "Z"), NOW))
				.isInstanceOf(ItineraryRevision.DayOrderMismatchException.class)
				.hasMessageContaining("모르는 항목 1개");
	}

	@Test
	@DisplayName("🔴 고정된 방문지가 옮겨지면 거부한다 — 고정이라는 약속이 무의미해진다")
	void movingALockedItemIsRejected() {
		ItineraryContent withLocked = new ItineraryContent(null,
				List.of(item("A", 0, 1, LocalTime.of(9, 0), false),
						item("B", 0, 2, LocalTime.of(11, 0), true),
						item("C", 0, 3, LocalTime.of(13, 0), false)),
				List.of(), List.of());

		assertThatThrownBy(() -> ItineraryRevision.withReorderedDay(
				withLocked, "ver_new", 0, List.of("B", "A", "C"), NOW))
				.isInstanceOf(ItineraryRevision.LockedItemMovedException.class)
				.hasMessageContaining("B");
	}

	@Test
	@DisplayName("고정된 방문지가 제자리면 나머지는 바꿀 수 있다 — 고정은 그 항목만 묶는다")
	void reorderingAroundALockedItemIsAllowed() {
		ItineraryContent withLocked = new ItineraryContent(null,
				List.of(item("A", 0, 1, LocalTime.of(9, 0), false),
						item("B", 0, 2, LocalTime.of(11, 0), true),
						item("C", 0, 3, LocalTime.of(13, 0), false)),
				List.of(), List.of());

		ItineraryRevision.Draft draft = ItineraryRevision.withReorderedDay(
				withLocked, "ver_new", 0, List.of("C", "B", "A"), NOW);

		assertThat(dayOf(draft, 0)).extracting(ItineraryItem::itemKey).containsExactly("C", "B", "A");
	}

	@Test
	@DisplayName("시각이 없는 항목은 없는 채로 옮겨진다 — 없는 것을 지어내지 않는다")
	void itemsWithoutTimesStayWithoutTimes() {
		ItineraryContent noTimes = new ItineraryContent(null,
				List.of(item("A", 0, 1, null, false), item("B", 0, 2, null, false)),
				List.of(), List.of());

		ItineraryRevision.Draft draft = ItineraryRevision.withReorderedDay(
				noTimes, "ver_new", 0, List.of("B", "A"), NOW);

		assertThat(dayOf(draft, 0)).extracting(ItineraryItem::startTime).containsOnlyNulls();
	}
}
