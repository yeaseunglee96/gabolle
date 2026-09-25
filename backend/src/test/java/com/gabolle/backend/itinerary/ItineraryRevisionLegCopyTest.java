package com.gabolle.backend.itinerary;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.itinerary.domain.ItineraryContent;
import com.gabolle.backend.itinerary.domain.ItineraryItem;
import com.gabolle.backend.itinerary.domain.ItineraryLeg;
import com.gabolle.backend.itinerary.domain.ItineraryRevision;
import com.gabolle.backend.itinerary.domain.ItineraryVersion;

/**
 * 판을 그대로 옮길 때 구간의 요금 · 선형(지도 위 길)이 따라간다 (S15P21E201-1706).
 *
 * <p>전에는 이 복사가 두 칸을 흘려서, 고정 · 장소 추가 · 남은 하루 재계획 · 되돌리기 뒤의 판은 같은 구간인데도 길 선이
 * 점선이 되고 교통비 줄이 사라졌다. 복사는 구간 하나를 같은 출발 곳 · 도착 곳 · 수단 그대로 옮기므로 그 길과 요금이
 * 여전히 참이다. 그날 순서를 바꾸면 쌍이 바뀌므로 그날 구간은 옮기지 않는다(다른 자리가 새로 잰다) — 그것도 본다.
 */
class ItineraryRevisionLegCopyTest {

	private static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");

	private static final LocalDate DAY1 = LocalDate.of(2026, 10, 15);

	private final ItineraryVersion base = new ItineraryVersion(UUID.randomUUID().toString(), "itn_1", 1, null,
			ItineraryVersion.Operation.CREATE, "usr_1", "req_1", null, NOW);

	/** 첫날 셋(A·B·C), 둘째 날 둘(D·E). 구간마다 선형 · 요금 · 보정 전 분이 있다. */
	private final List<String> places = List.of(id(), id(), id(), id(), id());

	private final List<ItineraryItem> items = List.of(item(0, 1, 0), item(0, 2, 1), item(0, 3, 2), item(1, 1, 3), item(1, 2, 4));

	private final List<ItineraryLeg> legs = List.of(
			leg(0, 2, 0, 1, 1_300), leg(0, 3, 1, 2, 2_100),
			leg(1, 2, 3, 4, 4_800));

	private final ItineraryContent content = new ItineraryContent(this.base, this.items, this.legs, List.of());

	@Test
	@DisplayName("🔴 되돌리기(통째 복사) — 모든 구간의 요금 · 선형이 그대로")
	void copyKeepsFareAndPath() {
		assertKept(ItineraryRevision.copyOf(this.content, id(), NOW).legs(), this.legs);
	}

	@Test
	@DisplayName("🔴 고정 — 구간은 안 바뀌므로 요금 · 선형이 그대로")
	void lockKeepsFareAndPath() {
		assertKept(ItineraryRevision.setLocked(this.content, id(), this.items.get(1).itemKey(), true, NOW).legs(), this.legs);
	}

	@Test
	@DisplayName("장소 추가 — 있던 구간의 요금 · 선형이 그대로")
	void addKeepsFareAndPath() {
		assertKept(ItineraryRevision.withAddedItem(this.content, id(), id(), 0, DAY1, NOW).legs(), this.legs);
	}

	@Test
	@DisplayName("남은 하루 재계획(시각만 다시) — 자리가 안 바뀌므로 요금 · 선형이 그대로")
	void replanKeepsFareAndPath() {
		assertKept(ItineraryRevision.withReplannedDay(this.content, id(), 0,
				Map.of(this.items.get(1).itemKey(), LocalTime.of(13, 0)), Map.of(this.items.get(1).itemKey(), LocalTime.of(14, 0)),
				NOW).legs(), this.legs);
	}

	@Test
	@DisplayName("🔴 그날 순서 바꾸기 — 그날 구간은 옮기지 않고(쌍이 바뀐다) 다른 날 구간만 요금 · 선형째 옮긴다")
	void reorderMovesOnlyOtherDaysWithFareAndPath() {
		List<String> reversed = List.of(this.items.get(2).itemKey(), this.items.get(1).itemKey(), this.items.get(0).itemKey());

		List<ItineraryLeg> copied = ItineraryRevision.withReorderedDay(this.content, id(), 0, reversed, NOW).legs();

		assertThat(copied).allSatisfy((leg) -> assertThat(leg.dayIndex()).isEqualTo(1));
		assertKept(copied, List.of(this.legs.get(2)));
	}

	private static void assertKept(List<ItineraryLeg> copied, List<ItineraryLeg> originals) {
		assertThat(copied).hasSameSizeAs(originals);
		for (ItineraryLeg original : originals) {
			ItineraryLeg same = copied.stream()
					.filter((leg) -> leg.dayIndex() == original.dayIndex() && leg.toPlaceId().equals(original.toPlaceId()))
					.findFirst().orElseThrow();
			assertThat(same.fromPlaceId()).isEqualTo(original.fromPlaceId());
			assertThat(same.travelMode()).isEqualTo(original.travelMode());
			assertThat(same.fareKrw()).as("요금").isEqualTo(original.fareKrw());
			assertThat(same.path()).as("선형").isNotNull().hasSameSizeAs(original.path());
			assertThat(same.path().get(0)).containsExactly(original.path().get(0));
			assertThat(same.uncalibratedDurationMin()).isEqualTo(original.uncalibratedDurationMin());
		}
	}

	private ItineraryItem item(int day, int sequence, int place) {
		return new ItineraryItem(id(), this.base.itineraryVersionId(), id(), day, DAY1.plusDays(day), sequence,
				this.places.get(place), LocalTime.of(9 + sequence, 0), LocalTime.of(10 + sequence, 0), 60, false, null,
				ItineraryItem.DataStatus.VERIFIED, List.of(), List.of(), null, NOW);
	}

	private ItineraryLeg leg(int day, int sequence, int from, int to, int fare) {
		List<double[]> path = new ArrayList<>();
		path.add(new double[] { 129.0 + from / 100.0, 35.1 });
		path.add(new double[] { 129.0 + to / 100.0, 35.2 });
		return new ItineraryLeg(id(), this.base.itineraryVersionId(), day, sequence, this.places.get(from),
				this.places.get(to), "PRIVATE_CAR", 3_000, 12, null, null, null, ItineraryItem.DataStatus.VERIFIED, fare, path,
				10, NOW);
	}

	private static String id() {
		return UUID.randomUUID().toString();
	}
}
