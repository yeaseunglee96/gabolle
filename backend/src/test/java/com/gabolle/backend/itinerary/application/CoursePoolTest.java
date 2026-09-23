package com.gabolle.backend.itinerary.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.recommendation.application.port.ItineraryDraftCommand.PlannedPlace;

/**
 * 추천 코스 2안·3안의 후보 차례 (S15P21E201-1454). 일정 조립이 앞에서부터 앉히므로 이 차례가 곧
 * 「어느 곳이 들어가나」다.
 */
class CoursePoolTest {

	private final List<PlannedPlace> ranked = ranked(6);

	@Test
	@DisplayName("앞 코스가 안 쓴 곳이 먼저, 쓴 곳은 뒤로 — 각 무리 안에서는 순위 차례 그대로다")
	void unusedPlacesComeFirstInRankOrder() {
		Set<UUID> used = Set.of(id(1), id(2), id(3));

		List<PlannedPlace> ordered = CoursePool.order(this.ranked, Set.of(), used);

		assertThat(ordered).extracting(PlannedPlace::rank).containsExactly(4, 5, 6, 1, 2, 3);
	}

	@Test
	@DisplayName("🔴 꼭 가고 싶은 곳은 앞 코스가 썼어도 맨 앞이다 — 뒤로 보내면 2안부터 그 장소가 빠진다")
	void pinnedPlacesStayInFrontEvenIfUsed() {
		Set<UUID> used = Set.of(id(1), id(2), id(3));

		List<PlannedPlace> ordered = CoursePool.order(this.ranked, Set.of(id(2)), used);

		assertThat(ordered).extracting(PlannedPlace::rank).containsExactly(2, 4, 5, 6, 1, 3);
	}

	@Test
	@DisplayName("🔴 후보를 빼거나 더하지 않는다 — 조건을 통과한 후보가 그대로, 차례만 바뀐다")
	void neverDropsOrAddsCandidates() {
		List<PlannedPlace> ordered = CoursePool.order(this.ranked, Set.of(id(6)), Set.of(id(1), id(6)));

		assertThat(ordered).containsExactlyInAnyOrderElementsOf(this.ranked);
	}

	private static List<PlannedPlace> ranked(int count) {
		List<PlannedPlace> places = new ArrayList<>();
		for (int rank = 1; rank <= count; rank++) {
			places.add(new PlannedPlace(id(rank), rank, List.of(), List.of(), null));
		}
		return places;
	}

	/** 순위 번호로 정해지는 장소 번호 — 같은 순위는 늘 같은 장소다. */
	private static UUID id(int rank) {
		return new UUID(0L, rank);
	}
}
