package com.gabolle.backend.itinerary.application;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.itinerary.application.FoodPreferencePicks.Picks;
import com.gabolle.backend.place.domain.PlaceEvidenceStatus;
import com.gabolle.backend.place.domain.PlaceFeature;
import com.gabolle.backend.recommendation.application.port.ItineraryDraftCommand.PlannedPlace;
import com.gabolle.backend.trip.domain.PersonalizationScope;
import com.gabolle.backend.trip.domain.PreferenceSnapshot;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 고른 음식 취향이 일정에 최소 하나는 들어가게 하는 규칙의 «재료» — S15P21E201-2061.
 *
 * <p>저장소도 스프링도 없다. 여기서 보는 것은 순수한 계산 셋이다 — 무엇을 골랐나, 후보가 어느 음식인가, 일정이 무엇을 덮었나. 앉히는
 * 일(끼니 칸·날·영업시간)은 {@code ItineraryDraftFoodPreferenceTest} 가 본다.
 */
class FoodPreferencePicksTest {

	private static PreferenceSnapshot snapshotWith(String dimension, String valueJson, PreferenceSnapshot.AnswerStatus status) {
		return new PreferenceSnapshot("snap-1", "trip_1", 1,
				List.of(new PreferenceSnapshot.PreferenceAnswer(dimension, valueJson, status)),
				PersonalizationScope.TRIP, List.of(), Instant.parse("2026-09-05T00:00:00Z"));
	}

	private static PlaceFeature feature(UUID placeId, String type, String key, String value, PlaceEvidenceStatus status) {
		return PlaceFeature.imported(UUID.randomUUID(), placeId, type, key, value, status, "TEST", "t-1", null, null,
				OffsetDateTime.parse("2026-09-05T00:00:00Z"));
	}

	private static PlannedPlace place(int rank) {
		return new PlannedPlace(UUID.randomUUID(), rank, List.of(), List.of(), "FOOD");
	}

	@Test
	@DisplayName("고른 음식 코드를 고른 차례대로 읽는다 — 같은 코드는 한 번만")
	void chosenCodesKeepTheChosenOrder() {
		PreferenceSnapshot snapshot = snapshotWith("FOOD_PREFERENCE", "[\"PORK_SOUP\",\"MILMYEON\",\"PORK_SOUP\"]",
				PreferenceSnapshot.AnswerStatus.SELECTED);

		assertThat(FoodPreferencePicks.chosenOf(snapshot)).containsExactly("PORK_SOUP", "MILMYEON");
	}

	@Test
	@DisplayName("🔴 식단 낱말(채식·할랄)은 «찾을 음식» 이 아니다 — 후보를 거르는 조건이 이미 처리한다")
	void dietWordsAreNotFoodsToFind() {
		PreferenceSnapshot snapshot = snapshotWith("FOOD_PREFERENCE", "[\"VEGETARIAN\",\"SEAFOOD\",\"HALAL\"]",
				PreferenceSnapshot.AnswerStatus.SELECTED);

		assertThat(FoodPreferencePicks.chosenOf(snapshot)).containsExactly("SEAFOOD");
	}

	@Test
	@DisplayName("음식을 안 골랐거나(SELECTED 가 아님) 판이 없으면 고른 것이 없다 — 일정은 전과 똑같이 짜인다")
	void nothingChosenWhenNotAnsweredOrNoSnapshot() {
		assertThat(FoodPreferencePicks.chosenOf(null)).isEmpty();
		assertThat(FoodPreferencePicks.chosenOf(snapshotWith("FOOD_PREFERENCE", null, PreferenceSnapshot.AnswerStatus.UNKNOWN)))
				.isEmpty();
		assertThat(FoodPreferencePicks.chosenOf(snapshotWith("FOOD_PREFERENCE", null, PreferenceSnapshot.AnswerStatus.SKIPPED)))
				.isEmpty();
		assertThat(FoodPreferencePicks.chosenOf(snapshotWith("CATEGORY", "[\"FOOD\"]", PreferenceSnapshot.AnswerStatus.SELECTED)))
				.as("다른 차원의 답은 음식 취향이 아니다").isEmpty();
	}

	@Test
	@DisplayName("음식 종류 표식은 「있다고 확인된」 것만 센다 — 확인된 부재(false)·다른 유형은 안 센다")
	void onlyConfirmedCuisineTagsCount() {
		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		UUID c = UUID.randomUUID();

		Map<UUID, Set<String>> cuisines = FoodPreferencePicks.cuisinesOf(List.of(
				feature(a, "CUISINE_TAG", "PORK_SOUP", "true", PlaceEvidenceStatus.VERIFIED),
				feature(a, "CUISINE_TAG", "MILMYEON", "true", PlaceEvidenceStatus.ESTIMATED),
				feature(b, "CUISINE_TAG", "PORK_SOUP", "false", PlaceEvidenceStatus.VERIFIED),
				feature(c, "INTEREST_TAG", "PORK_SOUP", "true", PlaceEvidenceStatus.VERIFIED)));

		assertThat(cuisines.get(a)).containsExactlyInAnyOrder("PORK_SOUP", "MILMYEON");
		assertThat(cuisines).doesNotContainKeys(b, c);
	}

	@Test
	@DisplayName("고른 음식마다 그 음식 후보를 순위 차례로 모은다 — 후보가 없는 음식도 빈 목록으로 키가 남는다")
	void candidatesAreGroupedPerChosenCuisineInRankOrder() {
		PlannedPlace first = place(1);
		PlannedPlace second = place(2);
		PlannedPlace third = place(3);
		Picks picks = new Picks(List.of("PORK_SOUP", "MARKET"), Map.of(
				first.placeId(), Set.of("MILMYEON"),
				second.placeId(), Set.of("PORK_SOUP"),
				third.placeId(), Set.of("PORK_SOUP", "MILMYEON")));

		Map<String, List<PlannedPlace>> byCuisine = FoodPreferencePicks.candidatesByCuisine(picks, List.of(first, second, third));

		assertThat(byCuisine.keySet()).containsExactly("PORK_SOUP", "MARKET");
		assertThat(byCuisine.get("PORK_SOUP")).containsExactly(second, third);
		assertThat(byCuisine.get("MARKET")).as("후보가 없는 음식 — 부르는 쪽이 로그로 남긴다").isEmpty();
	}

	@Test
	@DisplayName("일정이 덮은 음식 — 한 곳이 두 음식이면 둘 다 덮는다")
	void aPlaceWithTwoTagsCoversBoth() {
		PlannedPlace both = place(1);
		PlannedPlace other = place(2);
		Picks picks = new Picks(List.of("PORK_SOUP", "MILMYEON", "SEAFOOD"), Map.of(
				both.placeId(), Set.of("PORK_SOUP", "MILMYEON"),
				other.placeId(), Set.of("SEAFOOD")));

		assertThat(FoodPreferencePicks.covered(picks, List.of(both.placeId()))).containsExactly("PORK_SOUP", "MILMYEON");
		assertThat(FoodPreferencePicks.covered(picks, List.of(other.placeId()))).containsExactly("SEAFOOD");
		assertThat(FoodPreferencePicks.covered(picks, List.of())).isEmpty();
		assertThat(picks.coveredBy(both.placeId())).containsExactly("PORK_SOUP", "MILMYEON");
	}
}
