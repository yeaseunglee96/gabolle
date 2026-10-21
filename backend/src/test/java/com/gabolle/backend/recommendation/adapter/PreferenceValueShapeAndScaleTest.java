package com.gabolle.backend.recommendation.adapter;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.place.api.PlaceCandidateResponse;
import com.gabolle.backend.place.api.PlaceFeatureView;
import com.gabolle.backend.place.domain.MatchKind;
import com.gabolle.backend.place.domain.UserPlaceCodeMap;
import com.gabolle.backend.recommendation.config.BaselineEngineProperties;
import com.gabolle.backend.recommendation.config.PreferenceAlignmentWeights;
import com.gabolle.backend.trip.domain.PersonalizationScope;
import com.gabolle.backend.trip.domain.PreferenceSnapshot;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 앱이 실제로 보내는 값이 점수에 반영되는가. 앱은 태그형을 맨 배열로, 점수형을 맨 정수
 * 1~5 로도 보내는데 모양이 안 맞으면 오류 없이 조용히 0점이 된다.
 *
 * 그래서 {@code PreferenceJson} 을 직접 부르지 않고 채점기를 통해 잰다 — 값이 파싱되는지가
 * 아니라 점수가 실제로 달라지는지를 봐야 한다.
 */
class PreferenceValueShapeAndScaleTest {

	private static final int RADIUS_M = 5000;

	private static final BaselineEngineProperties.Weights WEIGHTS =
			new BaselineEngineProperties.Weights(0.30, 0.20, 0.15, 0.15, 0.10, 0.10);

	private static final PreferenceAlignmentWeights ALIGNMENT_WEIGHTS =
			new PreferenceAlignmentWeights(null, null, null, null, null);

	private final ObjectMapper objectMapper = new ObjectMapper();

	private final BaselineCandidateScorer scorer = new BaselineCandidateScorer(this.objectMapper);

	private final List<UserPlaceCodeMap> preferenceCodeMap = List.of(
			codeMap("CATEGORY", "INTEREST_TAG", MatchKind.TAG_OVERLAP),
			codeMap("LOCALITY", "LOCALITY_SCORE", MatchKind.SCORE_COMPARE),
			codeMap("SLOPE_PREFERENCE", "SLOPE_PERCENT", MatchKind.SCORE_COMPARE),
			codeMap("SHADE_PREFERENCE", "SHADE_SCORE", MatchKind.SCORE_COMPARE));

	// ── 모양 ──────────────────────────────────────────────────────────────────

	@Test
	@DisplayName("🔴 앱이 보내는 맨 배열 [\"FOOD\"] 가 태그 겹침에 반영된다 — 전에는 언제나 빈 목록이었다")
	void 맨_배열_취향코드가_반영된다() {
		EngineCandidate result = score(
				candidate(List.of(tag("INTEREST_TAG", "FOOD", "ESTIMATED", "true"))),
				snapshot("CATEGORY", "[\"FOOD\"]"));

		assertThat(component(result, "interest")).isEqualTo(1.0);
		assertThat(result.reasonCodes()).contains("TAG_MATCH_INTEREST");
	}

	@Test
	@DisplayName("감싼 모양 {\"codes\":[...]} 도 그대로 읽힌다 — 한쪽을 버리지 않는다")
	void 감싼_모양도_읽힌다() {
		EngineCandidate result = score(
				candidate(List.of(tag("INTEREST_TAG", "FOOD", "ESTIMATED", "true"))),
				snapshot("CATEGORY", "{\"codes\":[\"FOOD\"]}"));

		assertThat(component(result, "interest")).isEqualTo(1.0);
	}

	@Test
	@DisplayName("고른 코드 둘 중 하나만 장소에 있으면 0.5 다 — 분모는 사용자가 고른 수")
	void 절반만_겹치면_절반이다() {
		// 관심 태그 어휘(NATURE·WALK)로 잰다. 후보의 갈래(FOOD)를 코드에 넣으면 「테마가 갈래와 같으면 만점」
		// 규칙(S15P21E201-1535)이 먼저 걸려 비율을 볼 수 없다.
		EngineCandidate result = score(
				candidate(List.of(tag("INTEREST_TAG", "NATURE", "ESTIMATED", "true"))),
				snapshot("CATEGORY", "[\"NATURE\",\"WALK\"]"));

		assertThat(component(result, "interest")).isEqualTo(0.5);
	}

	// ── 눈금 ──────────────────────────────────────────────────────────────────

	@Test
	@DisplayName("🔴 앱의 5단계 슬라이더 3 은 0.5 로 읽는다 — 전에는 clamp01 이 0 으로 뭉갰다")
	void 슬라이더_가운데는_절반이다() {
		// 장소 로컬성 0.5 · 사용자 답 3(=0.5) → 정렬도 1.0 → 총점에 0.10 이 그대로 들어간다.
		EngineCandidate result = score(
				candidate(List.of(value("LOCALITY_SCORE", "ESTIMATED", "0.5"))),
				snapshot("LOCALITY", "3"));

		assertThat(component(result, "preferenceAlignment")).isEqualTo(1.0);
	}

	@Test
	@DisplayName("슬라이더 양 끝 1·5 는 0.0·1.0 이다")
	void 슬라이더_양끝은_0과_1이다() {
		EngineCandidate lowest = score(
				candidate(List.of(value("LOCALITY_SCORE", "ESTIMATED", "0.0"))),
				snapshot("LOCALITY", "1"));
		EngineCandidate highest = score(
				candidate(List.of(value("LOCALITY_SCORE", "ESTIMATED", "1.0"))),
				snapshot("LOCALITY", "5"));

		assertThat(component(lowest, "preferenceAlignment")).isEqualTo(1.0);
		assertThat(component(highest, "preferenceAlignment")).isEqualTo(1.0);
	}

	@Test
	@DisplayName("이미 0~1 인 값은 그대로 쓴다 — 소수와 {\"score\":x} 둘 다")
	void 이미_0에서_1인_값은_그대로다() {
		EngineCandidate wrapped = score(
				candidate(List.of(value("LOCALITY_SCORE", "ESTIMATED", "0.7"))),
				snapshot("LOCALITY", "{\"score\":0.7}"));
		EngineCandidate bareDecimal = score(
				candidate(List.of(value("LOCALITY_SCORE", "ESTIMATED", "0.7"))),
				snapshot("LOCALITY", "0.7"));

		assertThat(component(wrapped, "preferenceAlignment")).isEqualTo(1.0);
		assertThat(component(bareDecimal, "preferenceAlignment")).isEqualTo(1.0);
	}

	// ── 낱말로 오는 답 — 경사 ───────────────────────────────

	@Test
	@DisplayName("🔴 「피하고 싶어요」는 평지를 가장 높게 친다 — 전에는 축이 통째로 빠졌다")
	void 경사를_피하고_싶어요는_평지가_가장_높다() {
		// 눈금 검산. 장소 경사만 0~100 이고 채점기가 100 으로 나눈다. 취향 쪽이 0~1 로
		// 나와야 평지(0%)에서 정렬도가 정확히 1.0 이 된다 — 그래서 부등호가 아니라 등호다.
		EngineCandidate flat = score(
				candidate(List.of(value("SLOPE_PERCENT", "ESTIMATED", "0"))),
				snapshot("SLOPE_PREFERENCE", "\"AVOID\""));
		EngineCandidate steep = score(
				candidate(List.of(value("SLOPE_PERCENT", "ESTIMATED", "30"))),
				snapshot("SLOPE_PREFERENCE", "\"AVOID\""));

		assertThat(component(flat, "preferenceAlignment")).isEqualTo(1.0);
		assertThat(component(steep, "preferenceAlignment")).isEqualTo(0.7);
		assertThat(component(flat, "preferenceAlignment"))
				.isGreaterThan(component(steep, "preferenceAlignment"));
		assertThat(flat.reasonCodes()).contains("PREF_ALIGNED_SLOPE_PREFERENCE");
	}

	@Test
	@DisplayName("🔴 「상관없어요」는 항이 빠진다 — 0 이면 「평지 선호」와 같은 뜻이 된다")
	void 경사가_상관없어요면_항이_빠진다() {
		EngineCandidate result = score(
				candidate(List.of(value("SLOPE_PERCENT", "ESTIMATED", "30"))),
				snapshot("SLOPE_PREFERENCE", "\"ALLOW\""));

		assertThat(component(result, "preferenceAlignment")).isNull();
		assertThat(result.reasonCodes()).doesNotContain("PREF_ALIGNED_SLOPE_PREFERENCE");
	}

	@Test
	@DisplayName("모르는 낱말도 항이 빠진다 — 지어내지 않는다")
	void 모르는_낱말은_항이_빠진다() {
		EngineCandidate result = score(
				candidate(List.of(value("SLOPE_PERCENT", "ESTIMATED", "30"))),
				snapshot("SLOPE_PREFERENCE", "\"MAYBE\""));

		assertThat(component(result, "preferenceAlignment")).isNull();
	}

	// ── 낱말로 오는 답 — 그늘 ───────────────────────────────

	@Test
	@DisplayName("🔴 「그늘길 우선」은 그늘이 많은 곳을 높게 친다 — 전에는 축이 통째로 빠졌다")
	void 그늘길_우선은_그늘_많은_곳이_높다() {
		// 눈금 검산. 경사와 달리 그늘은 장소 값도 취향 값도 둘 다 0~1 이라 100 으로 나누는
		// 자리가 없다. 취향 1.0 · 장소 0.9 면 1 - |1.0 - 0.9| = 0.9 가 정확히 나와야 한다.
		EngineCandidate shady = score(
				candidate(List.of(value("SHADE_SCORE", "ESTIMATED", "0.9"))),
				snapshot("SHADE_PREFERENCE", "\"PREFER\""));
		EngineCandidate sunny = score(
				candidate(List.of(value("SHADE_SCORE", "ESTIMATED", "0.1"))),
				snapshot("SHADE_PREFERENCE", "\"PREFER\""));

		// 허용오차는 2진 소수 오차만 흡수한다 — 1 - |1.0 - 0.1| 이 0.09999999999999998 로
		// 떨어진다. 눈금이 100배 어긋나면 이 폭으로는 지나가지 못한다.
		assertThat(component(shady, "preferenceAlignment")).isCloseTo(0.9, within(1e-9));
		assertThat(component(sunny, "preferenceAlignment")).isCloseTo(0.1, within(1e-9));
		assertThat(component(shady, "preferenceAlignment"))
				.isGreaterThan(component(sunny, "preferenceAlignment"));
		assertThat(shady.reasonCodes()).contains("PREF_ALIGNED_SHADE_PREFERENCE");
		// 🔴 뙤약볕(정렬도 0.1)에 「그늘 취향에 맞음」을 붙이면 거짓말이다 — 정렬도 0.5 미만은 이유 코드가 없다.
		assertThat(sunny.reasonCodes()).doesNotContain("PREF_ALIGNED_SHADE_PREFERENCE");
	}

	@Test
	@DisplayName("정렬도가 딱 0.5 이면 이유 코드가 붙는다 — 경계는 「이상」이다")
	void 정렬도_경계값은_이유코드가_붙는다() {
		EngineCandidate half = score(
				candidate(List.of(value("SHADE_SCORE", "ESTIMATED", "0.5"))),
				snapshot("SHADE_PREFERENCE", "\"PREFER\""));

		assertThat(component(half, "preferenceAlignment")).isCloseTo(0.5, within(1e-9));
		assertThat(half.reasonCodes()).contains("PREF_ALIGNED_SHADE_PREFERENCE");
	}

	@Test
	@DisplayName("🔴 그늘이 「상관없어요」면 항이 빠진다 — 0 이면 「볕 선호」와 같은 뜻이 된다")
	void 그늘이_상관없어요면_항이_빠진다() {
		EngineCandidate result = score(
				candidate(List.of(value("SHADE_SCORE", "ESTIMATED", "0.9"))),
				snapshot("SHADE_PREFERENCE", "\"NO_PREFERENCE\""));

		// 0 으로 두면 안 따진다고 답한 사람을 골라서 뙤약볕으로 보내면서 아무 오류도 안 난다.
		assertThat(component(result, "preferenceAlignment")).isNull();
		assertThat(result.reasonCodes()).doesNotContain("PREF_ALIGNED_SHADE_PREFERENCE");
	}

	@Test
	@DisplayName("그늘도 모르는 낱말이면 항이 빠진다 — 지어내지 않는다")
	void 그늘의_모르는_낱말은_항이_빠진다() {
		EngineCandidate result = score(
				candidate(List.of(value("SHADE_SCORE", "ESTIMATED", "0.9"))),
				snapshot("SHADE_PREFERENCE", "\"MAYBE\""));

		assertThat(component(result, "preferenceAlignment")).isNull();
	}

	// ── 장소 값이 없을 때 — 모름이 평균보다 이기면 안 된다 ────────────────────────

	@Test
	@DisplayName("🔴 그늘 자료가 없는 곳이 그늘이 중간인 곳을 이기지 않는다 — 경사 피함 + 그늘 우선, 둘 다 경사 3%")
	void 그늘_모름이_그늘_중간을_이기지_않는다() {
		PreferenceSnapshot slopeAvoidShadePrefer = answers(
				"SLOPE_PREFERENCE", "\"AVOID\"", "SHADE_PREFERENCE", "\"PREFER\"");
		EngineCandidate median = score(
				candidate(List.of(value("SLOPE_PERCENT", "ESTIMATED", "3"),
						value("SHADE_SCORE", "ESTIMATED", "0.5"))),
				slopeAvoidShadePrefer);
		EngineCandidate unknownShade = score(
				candidate(List.of(value("SLOPE_PERCENT", "ESTIMATED", "3"))),
				slopeAvoidShadePrefer);

		// 예전: 중간인 곳 (0.97 + 0.5) / 2 = 0.735, 모르는 곳은 그늘 축이 빠져 0.97 — 모르는 곳이 이겼다.
		// 지금: 모르는 곳도 그늘 축을 1 - (1² + 0²) / 2 = 0.5 로 채워 0.735 — 이기지 못한다.
		assertThat(component(median, "preferenceAlignment")).isCloseTo(0.735, within(1e-9));
		assertThat(component(unknownShade, "preferenceAlignment")).isCloseTo(0.735, within(1e-9));
		assertThat(component(unknownShade, "preferenceAlignment"))
				.isLessThanOrEqualTo(component(median, "preferenceAlignment"));
		assertThat(unknownShade.preRankScore()).isLessThanOrEqualTo(median.preRankScore());

		// 채운 축은 맞춘 것이 아니다 — 이유 코드가 없고, 설명 쪽에는 「모름」(null)이 남는다.
		assertThat(unknownShade.reasonCodes()).doesNotContain("PREF_ALIGNED_SHADE_PREFERENCE");
		assertThat(unknownShade.reasonCodes()).contains("PREF_ALIGNED_SLOPE_PREFERENCE");
		assertThat(unknownShade.featureValues()).containsEntry("shadeScore", null);
		assertThat(imputed(unknownShade)).containsExactly("SHADE_PREFERENCE");
		assertThat(imputed(median)).isEmpty();
	}

	@Test
	@DisplayName("답한 축 하나뿐인데 장소 값이 없으면 항이 빠지지 않고 중간값이다 — 한쪽 끝 취향이면 0.5")
	void 답한_축의_장소_값이_없으면_중간값이다() {
		EngineCandidate result = score(candidate(List.of()), snapshot("SHADE_PREFERENCE", "\"PREFER\""));

		assertThat(component(result, "preferenceAlignment")).isCloseTo(0.5, within(1e-9));
		assertThat(result.reasonCodes()).doesNotContain("PREF_ALIGNED_SHADE_PREFERENCE");
	}

	@Test
	@DisplayName("답하지 않은 축은 장소 값이 없어도 예전처럼 빠진다 — 채우는 것은 답한 축뿐이다")
	void 답하지_않은_축은_그대로_빠진다() {
		EngineCandidate result = score(candidate(List.of()), snapshot("SHADE_PREFERENCE", "\"NO_PREFERENCE\""));

		assertThat(component(result, "preferenceAlignment")).isNull();
		assertThat(imputed(result)).isEmpty();
	}

	// ── 여기서 고치지 않는 것 ─────────────────────────────────────────────────

	@Test
	@DisplayName("🔴 어휘를 안 정한 차원의 낱말은 그대로 빠진다 — 지어내지 않는다")
	void 어휘를_안_정한_차원의_낱말은_항이_빠진다() {
		EngineCandidate result = score(
				candidate(List.of(value("LOCALITY_SCORE", "ESTIMATED", "0.9"))),
				snapshot("LOCALITY", "\"PREFER\""));

		// 같은 낱말이라도 차원이 다르면 뜻이 다르다. 경사·그늘만 사람이 어휘를 정했다.
		assertThat(component(result, "preferenceAlignment")).isNull();
	}

	// ── 도구 ──────────────────────────────────────────────────────────────────

	private EngineCandidate score(PlaceCandidateResponse.Candidate candidate, PreferenceSnapshot snapshot) {
		return this.scorer.score(candidate, snapshot, List.of(), RADIUS_M, WEIGHTS, ALIGNMENT_WEIGHTS,
				this.preferenceCodeMap, List.of(), List.of(), 0.05);
	}

	@SuppressWarnings("unchecked")
	private static Double component(EngineCandidate candidate, String key) {
		Map<String, Object> detail = (Map<String, Object>) candidate.scoreComponents().get(key);
		return (Double) detail.get("value");
	}

	@SuppressWarnings("unchecked")
	private static List<String> imputed(EngineCandidate candidate) {
		Map<String, Object> detail = (Map<String, Object>) candidate.scoreComponents().get("preferenceAlignment");
		return (List<String>) detail.get("imputedDimensions");
	}

	/** 답 여러 개 — {@code 차원, 값 JSON} 을 번갈아 준다. */
	private static PreferenceSnapshot answers(String... dimensionAndValue) {
		List<PreferenceSnapshot.PreferenceAnswer> list = new java.util.ArrayList<>();
		for (int i = 0; i < dimensionAndValue.length; i += 2) {
			list.add(new PreferenceSnapshot.PreferenceAnswer(dimensionAndValue[i], dimensionAndValue[i + 1],
					PreferenceSnapshot.AnswerStatus.SELECTED));
		}
		return new PreferenceSnapshot(UUID.randomUUID().toString(), UUID.randomUUID().toString(), 1, list,
				PersonalizationScope.TRIP, List.of(), Instant.now());
	}

	private PlaceCandidateResponse.Candidate candidate(List<PlaceFeatureView> features) {
		return new PlaceCandidateResponse.Candidate(UUID.randomUUID(), "테스트 장소", "FOOD", 35.1, 129.0,
				1000L, features);
	}

	private PlaceFeatureView tag(String featureType, String featureKey, String evidenceStatus, String raw) {
		return new PlaceFeatureView(featureType, featureKey, evidenceStatus, json(raw), null, "FIXTURE");
	}

	private PlaceFeatureView value(String featureType, String evidenceStatus, String raw) {
		return new PlaceFeatureView(featureType, null, evidenceStatus, json(raw), null, "FIXTURE");
	}

	private JsonNode json(String raw) {
		return this.objectMapper.readTree(raw);
	}

	private static UserPlaceCodeMap codeMap(String userInputCode, String placeFeatureType, MatchKind matchKind) {
		UserPlaceCodeMap row = mock(UserPlaceCodeMap.class);
		when(row.getUserInputCode()).thenReturn(userInputCode);
		when(row.getPlaceFeatureType()).thenReturn(placeFeatureType);
		when(row.getMatchKind()).thenReturn(matchKind);
		return row;
	}

	private static PreferenceSnapshot snapshot(String dimension, String valueJson) {
		return new PreferenceSnapshot(UUID.randomUUID().toString(), UUID.randomUUID().toString(), 1,
				List.of(new PreferenceSnapshot.PreferenceAnswer(dimension, valueJson,
						PreferenceSnapshot.AnswerStatus.SELECTED)),
				PersonalizationScope.TRIP, List.of(), Instant.now());
	}
}
