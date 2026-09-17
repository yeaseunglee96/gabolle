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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 🔴 <b>앱이 실제로 보내는 값이 점수에 반영되는가</b> — S15P21E201-635.
 *
 * <p>{@code PreferenceJson} 은 태그형이 {@code {"codes":[...]}} 로, 점수형이
 * {@code {"score":0~1}} 로 온다고 가정하고 있었다. 앱은 <b>맨 배열</b>과 <b>맨 정수 1~5</b> 를
 * 보낸다. 둘 다 오류를 내지 않고 <b>조용히 0점</b>이 됐다 — 사용자는 취향을 골랐는데 점수에는
 * 아무것도 안 들어갔고 로그에도 안 남았다.
 *
 * <p>그래서 이 테스트는 {@code PreferenceJson}(package-private)을 직접 부르지 않고
 * <b>채점기를 통해</b> 잰다. 값이 파싱되는지가 아니라 <b>점수가 실제로 달라지는지</b>가
 * 이 결함의 내용이기 때문이다.
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
		EngineCandidate result = score(
				candidate(List.of(tag("INTEREST_TAG", "FOOD", "ESTIMATED", "true"))),
				snapshot("CATEGORY", "[\"FOOD\",\"SEA_BEACH\"]"));

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

	// ── 낱말로 오는 답 — 경사 (S15P21E201-1188) ───────────────────────────────

	@Test
	@DisplayName("🔴 「피하고 싶어요」는 평지를 가장 높게 친다 — 전에는 축이 통째로 빠졌다")
	void 경사를_피하고_싶어요는_평지가_가장_높다() {
		// 🔴 눈금 검산. 장소 경사만 0~100 이고 채점기가 100 으로 나눈다. 취향 쪽이 0~1 로
		//    나와야 평지(0%)에서 정렬도가 정확히 1.0 이 된다 — 100 을 내놓았다면 clamp01 이
		//    1.0 으로 뭉개서 여기가 0.0 으로 뒤집힌다. 그래서 이 값은 부등호가 아니라 등호다.
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

	// ── 여기서 고치지 않는 것 ─────────────────────────────────────────────────

	@Test
	@DisplayName("🔴 낱말로 오는 답(\"PREFER\")은 항이 빠진다 — 0 이 아니다. 어휘는 사람이 정한다")
	void 낱말로_오는_답은_항이_빠진다() {
		EngineCandidate result = score(
				candidate(List.of(value("SHADE_SCORE", "ESTIMATED", "0.9"))),
				snapshot("SHADE_PREFERENCE", "\"PREFER\""));

		// null 이어야 한다. 0 이면 "가장 낮게 답했다" 는 뜻이 되어 그늘 많은 곳이 오히려 깎인다.
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
