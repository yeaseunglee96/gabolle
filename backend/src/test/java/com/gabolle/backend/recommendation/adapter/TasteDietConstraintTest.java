package com.gabolle.backend.recommendation.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.place.api.PlaceCandidateResponse;
import com.gabolle.backend.place.domain.MatchKind;
import com.gabolle.backend.place.domain.UserPlaceCodeMap;
import com.gabolle.backend.recommendation.config.BaselineEngineProperties;
import com.gabolle.backend.recommendation.config.PreferenceAlignmentWeights;
import com.gabolle.backend.recommendation.domain.ConstraintVerdict;
import com.gabolle.backend.trip.domain.PersonalizationScope;
import com.gabolle.backend.trip.domain.PreferenceSnapshot;
import com.gabolle.backend.trip.domain.TripConstraint;

import tools.jackson.databind.ObjectMapper;

/**
 * 음식 취향의 채식이 식단 조건과 같은 판정으로 돈다(S15P21E201-1873).
 *
 * <p>운영 여행 51bfca7f 는 음식 취향으로 채식을 골랐는데 식단 조건은 할랄뿐이라, 한우반상·동백섬횟집이 채식 판정을 안 받고
 * 일정에 들어갔다.
 */
class TasteDietConstraintTest {

	private final BaselineCandidateScorer scorer = new BaselineCandidateScorer(new ObjectMapper());

	private final List<UserPlaceCodeMap> constraintCodeMap = List.of(codeMap());

	@Test
	@DisplayName("음식 취향에 채식이 있으면 식단 조건 채식이 더해져 한우 집·횟집이 빠진다")
	void 음식_취향의_채식이_식단_조건이_된다() {
		List<TripConstraint> merged = this.scorer.withTasteDiets(List.of(diet("HALAL", TripConstraint.AnswerStatus.SELECTED)),
				snapshot("[\"VEGETARIAN\",\"SEAFOOD\"]"));

		assertThat(merged).extracting(TripConstraint::constraintKey).containsExactlyInAnyOrder("HALAL", "VEGETARIAN");
		assertThat(merged).filteredOn(c -> "VEGETARIAN".equals(c.constraintKey())).singleElement()
				.satisfies(c -> assertThat(c.dietRequirement()).isEqualTo(TripConstraint.DietRequirement.REQUIRED));
		for (String name : List.of("한우반상", "동백섬횟집")) {
			assertThat(score(name, merged).constraintVerdict()).as(name).isEqualTo(ConstraintVerdict.FAIL);
		}
		assertThat(score("할매밥집 한정식", merged).constraintVerdict()).isEqualTo(ConstraintVerdict.PASS);
	}

	@Test
	@DisplayName("이미 식단 조건에 채식이 있으면 두 벌로 만들지 않는다")
	void 이미_있으면_안_더한다() {
		List<TripConstraint> merged = this.scorer.withTasteDiets(
				List.of(diet("VEGETARIAN", TripConstraint.AnswerStatus.SELECTED)), snapshot("[\"VEGETARIAN\"]"));

		assertThat(merged).hasSize(1);
	}

	@Test
	@DisplayName("이번 여행에서 식단을 「없음」으로 답했으면 계정 취향의 채식을 더하지 않는다")
	void 식단_없음이_이긴다() {
		List<TripConstraint> merged = this.scorer.withTasteDiets(
				List.of(diet("NONE", TripConstraint.AnswerStatus.NONE)), snapshot("[\"VEGETARIAN\"]"));

		assertThat(merged).extracting(TripConstraint::constraintKey).containsExactly("NONE");
	}

	@Test
	@DisplayName("식단이 아닌 음식 취향(해산물·밀면)은 조건을 만들지 않는다")
	void 식단이_아닌_취향은_그대로() {
		assertThat(this.scorer.withTasteDiets(List.of(), snapshot("[\"SEAFOOD\",\"MILMYEON\"]"))).isEmpty();
		assertThat(this.scorer.withTasteDiets(List.of(), null)).isEmpty();
	}

	private EngineCandidate score(String name, List<TripConstraint> constraints) {
		PlaceCandidateResponse.Candidate candidate = new PlaceCandidateResponse.Candidate(UUID.randomUUID(), name, "FOOD",
				35.1, 129.0, 1000L, List.of());
		return this.scorer.score(candidate, null, constraints, 5000,
				new BaselineEngineProperties.Weights(0.30, 0.20, 0.15, 0.15, 0.10, 0.10),
				new PreferenceAlignmentWeights(null, null, null, null, null), List.of(), this.constraintCodeMap,
				List.of(), 0.05);
	}

	private static PreferenceSnapshot snapshot(String foods) {
		return new PreferenceSnapshot(UUID.randomUUID().toString(), "trip-1", 1,
				List.of(new PreferenceSnapshot.PreferenceAnswer("FOOD_PREFERENCE", foods,
						PreferenceSnapshot.AnswerStatus.SELECTED)),
				PersonalizationScope.TRIP, List.of(), Instant.now());
	}

	private static TripConstraint diet(String code, TripConstraint.AnswerStatus status) {
		return new TripConstraint(UUID.randomUUID().toString(), "trip-1", "DIET", code, TripConstraint.Severity.HARD,
				"EXCLUDES", null, null, TripConstraint.EvidenceStatus.VERIFIED, status, PersonalizationScope.TRIP,
				status == TripConstraint.AnswerStatus.SELECTED ? TripConstraint.DietRequirement.REQUIRED : null);
	}

	private static UserPlaceCodeMap codeMap() {
		UserPlaceCodeMap row = mock(UserPlaceCodeMap.class);
		when(row.getUserInputCode()).thenReturn("DIET");
		when(row.getPlaceFeatureType()).thenReturn("DIETARY_SUPPORT_TAG");
		when(row.getMatchKind()).thenReturn(MatchKind.HARD_FILTER);
		return row;
	}
}
