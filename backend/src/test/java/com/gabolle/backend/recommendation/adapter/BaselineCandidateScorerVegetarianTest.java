package com.gabolle.backend.recommendation.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.place.api.PlaceCandidateResponse;
import com.gabolle.backend.place.api.PlaceFeatureView;
import com.gabolle.backend.place.domain.MatchKind;
import com.gabolle.backend.place.domain.UserPlaceCodeMap;
import com.gabolle.backend.recommendation.config.BaselineEngineProperties;
import com.gabolle.backend.recommendation.config.PreferenceAlignmentWeights;
import com.gabolle.backend.recommendation.domain.ConstraintVerdict;
import com.gabolle.backend.trip.domain.PersonalizationScope;
import com.gabolle.backend.trip.domain.TripConstraint;

import tools.jackson.databind.ObjectMapper;

/**
 * 채식·비건을 고르면 고기가 중심인 집이 빠진다 — S15P21E201-1815 (QA: 채식인데 고기집이 추천됨).
 * 식단 지원 표식(DIETARY_SUPPORT_TAG)이 운영에 0건이라 전에는 전부 「확인 안 됨」 경고로 통과했다.
 */
class BaselineCandidateScorerVegetarianTest {

	private final BaselineCandidateScorer scorer = new BaselineCandidateScorer(new ObjectMapper());

	private final List<UserPlaceCodeMap> constraintCodeMap = List.of(
			codeMap("DIET", "DIETARY_SUPPORT_TAG", MatchKind.HARD_FILTER));

	@Test
	@DisplayName("🔴 채식이면 갈비·삼겹살·곱창·족발·치킨집은 FAIL 이다")
	void 채식이면_고기집이_빠진다() {
		for (String name : List.of("해운대암소갈비집", "돼지 삼겹살 전문", "OO곱창", "원조족발", "BBQ치킨 서면점", "소고기국밥")) {
			EngineCandidate result = score(candidate(name, List.of()), diet("VEGETARIAN"));
			assertThat(result.constraintVerdict()).as(name).isEqualTo(ConstraintVerdict.FAIL);
			assertThat(result.violations()).as(name)
					.anySatisfy(v -> assertThat(v.get("code")).isEqualTo("DIET_NOT_SUPPORTED"));
		}
	}

	@Test
	@DisplayName("비건도 같다. 돼지국밥 태그만 붙은 집도 빠진다")
	void 비건과_돼지국밥태그() {
		assertThat(score(candidate("부산할매집", List.of(new PlaceFeatureView("CUISINE_TAG", "PORK_SOUP",
				"ESTIMATED", null, null, "FIXTURE"))), diet("VEGAN")).constraintVerdict())
				.isEqualTo(ConstraintVerdict.FAIL);
		assertThat(score(candidate("한우명가", List.of()), diet("VEGAN")).constraintVerdict())
				.isEqualTo(ConstraintVerdict.FAIL);
	}

	@Test
	@DisplayName("고기와 상관없는 집·물고기 체험관은 빼지 않는다 — 예전처럼 경고만")
	void 고기집이_아니면_그대로() {
		for (String name : List.of("비빔밥 카페", "물고기 체험관", "해운대 칼국수")) {
			EngineCandidate result = score(candidate(name, List.of()), diet("VEGETARIAN"));
			assertThat(result.constraintVerdict()).as(name).isEqualTo(ConstraintVerdict.PASS);
			assertThat(result.warningCodes()).contains("DIET_SUPPORT_UNVERIFIED");
		}
	}

	@Test
	@DisplayName("채식을 안 고른 사람에게는 고기집이 그대로 나온다")
	void 다른_식단이면_안뺀다() {
		assertThat(score(candidate("해운대암소갈비집", List.of()), diet("HALAL")).constraintVerdict())
				.isNotEqualTo(ConstraintVerdict.FAIL);
		assertThat(score(candidate("해운대암소갈비집", List.of()), null).constraintVerdict())
				.isEqualTo(ConstraintVerdict.PASS);
	}

	private EngineCandidate score(PlaceCandidateResponse.Candidate candidate, TripConstraint constraint) {
		return this.scorer.score(candidate, null, constraint == null ? List.of() : List.of(constraint), 5000,
				new BaselineEngineProperties.Weights(0.30, 0.20, 0.15, 0.15, 0.10, 0.10),
				new PreferenceAlignmentWeights(null, null, null, null, null), List.of(), this.constraintCodeMap,
				List.of(), 0.05);
	}

	private static PlaceCandidateResponse.Candidate candidate(String name, List<PlaceFeatureView> features) {
		return new PlaceCandidateResponse.Candidate(UUID.randomUUID(), name, "FOOD", 35.1, 129.0, 1000L, features);
	}

	private static TripConstraint diet(String code) {
		return new TripConstraint(UUID.randomUUID().toString(), "trip-1", "DIET", code, TripConstraint.Severity.HARD,
				"EXCLUDES", null, null, TripConstraint.EvidenceStatus.VERIFIED, TripConstraint.AnswerStatus.SELECTED,
				PersonalizationScope.TRIP, TripConstraint.DietRequirement.REQUIRED);
	}

	private static UserPlaceCodeMap codeMap(String userInputCode, String placeFeatureType, MatchKind matchKind) {
		UserPlaceCodeMap row = mock(UserPlaceCodeMap.class);
		when(row.getUserInputCode()).thenReturn(userInputCode);
		when(row.getPlaceFeatureType()).thenReturn(placeFeatureType);
		when(row.getMatchKind()).thenReturn(matchKind);
		return row;
	}
}
