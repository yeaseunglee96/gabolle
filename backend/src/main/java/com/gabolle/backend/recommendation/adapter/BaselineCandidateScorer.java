package com.gabolle.backend.recommendation.adapter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.gabolle.backend.place.api.PlaceCandidateResponse;
import com.gabolle.backend.place.api.PlaceFeatureView;
import com.gabolle.backend.place.domain.FeaturePresence;
import com.gabolle.backend.place.domain.MatchKind;
import com.gabolle.backend.place.domain.UserPlaceCodeMap;
import com.gabolle.backend.preference.application.PreferenceJson;
import com.gabolle.backend.recommendation.application.RecommendationCodes;
import com.gabolle.backend.preference.domain.TasteDimension;
import com.gabolle.backend.preference.domain.UserTasteWeight;
import com.gabolle.backend.recommendation.config.BaselineEngineProperties;
import com.gabolle.backend.recommendation.config.PreferenceAlignmentWeights;
import com.gabolle.backend.recommendation.domain.CoarseArea;
import com.gabolle.backend.recommendation.domain.ConstraintVerdict;
import com.gabolle.backend.recommendation.domain.DistanceBucket;
import com.gabolle.backend.trip.domain.PreferenceSnapshot;
import com.gabolle.backend.trip.domain.TripConstraint;

import tools.jackson.databind.ObjectMapper;

/**
 * {@link PlaceCandidateResponse.Candidate} 하나를 {@link EngineCandidate} 하나로 만든다
 * (S15P21E201-604 — 이 작업의 핵심).
 *
 * <p>🔴 이 클래스는 <b>DB 를 모른다</b>. 사용자 입력 코드 ↔ 장소 표식 유형 대조는
 * {@code UserPlaceCodeMapRepository} 가 대신 읽어서 {@link UserPlaceCodeMap} 목록으로
 * 넘겨준다({@link BaselineRecommendationEngine} 이 배치당 한 번만 읽는다) — 후보마다
 * 다시 질의하면 "질의 개수가 후보 수에 비례하면 안 된다" 는 원칙을 어기게 된다. 그래서
 * 이 클래스는 순수 함수에 가깝고, {@code BaselineCandidateScorerTest} 가 DB 없이 돈다.
 *
 * <p>🔴 <b>severity 를 지어내지 않는다.</b> {@link #severityOf(TripConstraint)} 가 유일한
 * 통로다 — {@code HARD} 는 항상 {@code REQUIRED}, {@code SOFT} 는 항상 {@code PREFERRED}.
 * 알레르기는 DB CHECK 가 항상 HARD 를 강제하므로 자동으로 REQUIRED 가 된다.
 */
@Component
public class BaselineCandidateScorer {

	/** {@code EngineCandidate.candidateSource} — 어디서 왔는지 알 수 있는 값. */
	static final String CANDIDATE_SOURCE = "BASELINE_PLACE_QUERY";

	/**
	 * 🔴 이동 경고(계단·보행 상한 초과) 하나당 깎는 점수. 판정표는 "점수 감점" 이라고만
	 * 적었지 정확한 폭은 정하지 않았다 — 데이터가 쌓이면 조정될 값이라 설정으로 빼지 않고
	 * 상수로 뒀다(값 자체가 실험 대상이면 그때 설정으로 옮긴다).
	 */
	private static final double MOBILITY_WARNING_PENALTY = 0.05;

	/**
	 * 접근성을 <b>안 재 봤다</b>는 경고.
	 *
	 * <p>🔴 값은 {@link RecommendationCodes#WARNING_ACCESSIBILITY_UNVERIFIED} 하나뿐이다
	 * (S15P21E201-1158). 여기서 문자열을 다시 적지 않는다 — 세는 쪽({@code ItineraryQueryService})이
	 * 다른 갈래에 있어서, 두 벌이 되면 한쪽만 고쳐지는 날 <b>경고가 조용히 0건이 된다.</b>
	 */
	static final String ACCESSIBILITY_UNVERIFIED_WARNING = RecommendationCodes.WARNING_ACCESSIBILITY_UNVERIFIED;

	private final ObjectMapper objectMapper;

	public BaselineCandidateScorer(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	/**
	 * @param alignmentWeights 점수형 취향 다섯 차원이 {@code weights.preferenceAlignment} 를
	 *     나누는 비율 (S15P21E201-547). 🔴 빈으로 주입받지 않고 <b>인수로 받는다</b> —
	 *     {@code weights} 를 인수로 받는 것과 같은 이유다. 한 요청 안에서 설정이 다른 두 벌로
	 *     같은 후보를 채점해 견주는 것(S15P21E201-560 벤치마크)이 생성자 주입이면 불가능하다
	 * @param preferenceCodeMap {@code user_place_code_map} 의 {@code PREFERENCE} 행 전부
	 * @param constraintCodeMap {@code user_place_code_map} 의 {@code CONSTRAINT} 행 전부.
	 *     {@code MOBILITY} 는 {@code ACCESSIBILITY_TAG}(HARD_FILTER)·{@code STAIRS_PRESENT}
	 *     (FLAG_COMPARE) 둘에 걸리므로 코드가 아니라 이 목록에서 matchKind 로 가른다
	 */
	public EngineCandidate score(PlaceCandidateResponse.Candidate candidate,
			PreferenceSnapshot preferenceSnapshot, List<TripConstraint> constraints, int radiusM,
			BaselineEngineProperties.Weights weights, PreferenceAlignmentWeights alignmentWeights,
			List<UserPlaceCodeMap> preferenceCodeMap, List<UserPlaceCodeMap> constraintCodeMap,
			List<UserTasteWeight> tasteWeights, double tasteVectorMultiplier) {

		List<Map<String, Object>> violations = new ArrayList<>();
		List<Map<String, Object>> unknownFacts = new ArrayList<>();
		List<String> warnings = new ArrayList<>();

		evaluateConstraints(candidate, constraints, constraintCodeMap, violations, unknownFacts, warnings);

		ConstraintVerdict verdict;
		if (!violations.isEmpty()) {
			verdict = ConstraintVerdict.FAIL;
		}
		else if (!unknownFacts.isEmpty()) {
			verdict = ConstraintVerdict.UNKNOWN;
		}
		else {
			verdict = ConstraintVerdict.PASS;
		}

		Map<String, Object> featureValues = new LinkedHashMap<>();
		Map<String, Object> scoreComponents = new LinkedHashMap<>();
		List<String> reasonCodes = new ArrayList<>();

		double total = 0.0;

		// ── 다양성 재정렬이 쓸 값 (S15P21E201-548) ────────────────────────────
		//
		// 점수에 쓰지 않는다. "같은 종류인가 · 같은 동네인가" 를 판단하는 데만 쓰이고
		// (DiversityKeys), 그 판단은 순서만 바꾸고 점수는 건드리지 않는다. 여기 남기는
		// 이유는 CandidateAssembler 가 후보 객체만 보고 그 판단을 할 수 있어야 하기
		// 때문이다 — 장소 표를 다시 읽으면 질의 수가 후보 수에 비례하게 된다.
		//
		// 🔴 <b>좌표를 그대로 남기지 않는다.</b> lat·lng 를 넣었다가 SensitivePayloadGuard
		//    가 거부했고, 그 거부가 맞았다 — feature_values 는 일반 추천 로그이고 거기에는
		//    정밀 좌표를 남기지 않는다는 것이 이 저장소의 규칙이다(그 클래스 javadoc).
		//
		//    그래서 <b>이름만 바꿔 통과시키지 않고</b> 값 자체를 굵게 만든다. 소수점을 두
		//    자리에서 자른 정수 쌍이라 부산 위도에서 대략 1km 칸이고, 되돌려도 그 칸보다
		//    정밀한 위치가 나오지 않는다. 재정렬에 필요한 것은 "같은 칸인가" 하나뿐이므로
		//    잃는 것이 없다. 그 그물의 javadoc 이 "최후의 그물이지 설계 대체물이 아니다 —
		//    무엇을 담을지는 부르는 쪽이 정해야 한다" 고 적은 그 결정이 이것이다.
		featureValues.put("category", candidate.category());
		featureValues.put("localityBucket", CoarseArea.of(candidate.lat(), candidate.lng()));

		// ── 거리 — 항상 잴 수 있다 ────────────────────────────────────────────
		double distanceComponent = clamp01(1.0 - (candidate.distanceM() / (double) radiusM));
		featureValues.put("distanceM", candidate.distanceM());
		// 🔴 S15P21E201-550 — 장기 분석은 띠로 센다. 미터만 남기면 질의마다 경계를 다시
		//    정하게 되어 같은 지표가 사람마다 다른 숫자가 된다(DistanceBucket javadoc).
		featureValues.put("distanceBucket", DistanceBucket.of(candidate.distanceM()));
		scoreComponents.put("distance", componentDetail(weights.distance(), distanceComponent, null));
		reasonCodes.add("NEAR_ORIGIN");
		total += weights.distance() * distanceComponent;

		// ── 태그 겹침 셋 — 관심·분위기·음식 ──────────────────────────────────
		total += applyTagComponent(candidate, preferenceSnapshot, preferenceCodeMap, "CATEGORY",
				weights.interest(), "interest", "TAG_MATCH_INTEREST", "interestTagOverlap",
				featureValues, scoreComponents, reasonCodes);
		total += applyTagComponent(candidate, preferenceSnapshot, preferenceCodeMap, "ATMOSPHERE",
				weights.atmosphere(), "atmosphere", "TAG_MATCH_ATMOSPHERE", "atmosphereTagOverlap",
				featureValues, scoreComponents, reasonCodes);
		total += applyTagComponent(candidate, preferenceSnapshot, preferenceCodeMap, "FOOD_PREFERENCE",
				weights.cuisine(), "cuisine", "TAG_MATCH_CUISINE", "cuisineTagOverlap",
				featureValues, scoreComponents, reasonCodes);

		// ── 접힌 취향 벡터의 덧점수 (S15P21E201-943) ────────────────────────
		total += applyTasteVectorComponent(candidate, preferenceCodeMap, tasteWeights, tasteVectorMultiplier,
				featureValues, scoreComponents, reasonCodes);

		// ── 점수형 선호 다섯 — LOCALITY·QUIETNESS·TOURIST_PREFERENCE·SHADE_PREFERENCE·SLOPE_PREFERENCE
		//
		// 🔴 이 다섯은 weights.preferenceAlignment(기본 0.10) 하나를 나눠 쓴다. 나누는 방식이
		//    단순 평균이었다가 가중 평균으로 바뀌었다(S15P21E201-547) — 단순 평균이면 다섯이
		//    서로를 희석해서, 사용자가 가장 강하게 답한 축조차 총점에 0.10 ÷ 5 = 0.02 밖에
		//    기여하지 못했다. 거리(0.30)가 그것을 덮는다. 비율과 계산은 모두
		//    PreferenceAlignmentWeights 에 있다.
		Map<String, Double> alignments = new LinkedHashMap<>();
		applyAlignmentDimension(candidate, preferenceSnapshot, preferenceCodeMap, "LOCALITY", "localityScore",
				false, featureValues, alignments, reasonCodes);
		applyAlignmentDimension(candidate, preferenceSnapshot, preferenceCodeMap, "QUIETNESS", "quietnessScore",
				false, featureValues, alignments, reasonCodes);
		applyAlignmentDimension(candidate, preferenceSnapshot, preferenceCodeMap, "TOURIST_PREFERENCE",
				"touristRatio", false, featureValues, alignments, reasonCodes);
		applyAlignmentDimension(candidate, preferenceSnapshot, preferenceCodeMap, "SHADE_PREFERENCE", "shadeScore",
				false, featureValues, alignments, reasonCodes);
		// 🔴 SLOPE_PERCENT 는 0~100 퍼센트다. 선호값은 0~1 스케일이라고 가정한다(위 PreferenceJson
		// 참고) — 그래서 비교 전에 100 으로 나눠 같은 축으로 맞춘다.
		applyAlignmentDimension(candidate, preferenceSnapshot, preferenceCodeMap, "SLOPE_PREFERENCE", "slopePercent",
				true, featureValues, alignments, reasonCodes);

		Double alignmentAverage = alignmentWeights.weightedAverage(alignments);
		// 🔴 dimensions 옆에 dimensionWeights 를 같이 남긴다. 정렬도만 남기면 "이 장소가 왜 이
		//    순위인가" 를 되짚을 때 어느 축이 얼마나 셌는지를 알 수 없다 — 설정을 바꿔 실험하는
		//    쪽에서는 그 두 값이 함께 있어야 결과를 읽는다. S15P21E201-548(추천 이유 코드)이
		//    읽을 자리이기도 하다.
		scoreComponents.put("preferenceAlignment", componentDetail(weights.preferenceAlignment(), alignmentAverage,
				Map.of("dimensions", alignments, "dimensionWeights", alignmentWeights.weightsUsed(alignments))));
		if (alignmentAverage != null) {
			total += weights.preferenceAlignment() * alignmentAverage;
		}

		// ── 인기 — 사용자가 직접 고르는 화면이 없다. 짝이 없는 피처라 순수 점수 자체를 쓴다 ──
		Double popularity = extractPlaceScore(candidate, "POPULARITY_SCORE");
		featureValues.put("popularityScore", popularity);
		scoreComponents.put("popularity", componentDetail(weights.popularity(), popularity, null));
		if (popularity != null) {
			total += weights.popularity() * popularity;
			reasonCodes.add("POPULAR");
		}

		// ── 혼잡도 — 점수에는 안 쓰고 값만 남긴다 ────────────────────────────
		//
		// 🔴 순위를 매기는 데 쓰지 않는 값을 굳이 기록하는 이유. 결과 조회 API 가
		//    이 값을 읽어 "붐빔 정도" 를 화면에 내보낸다. 여기서 안 남기면 그 칸은
		//    영원히 비어 있고, 데이터가 실제로 있는데도 없는 것처럼 보인다.
		//    없으면 null 이고, 그 null 은 "안 붐빈다" 가 아니라 "모른다" 로 나간다.
		featureValues.put("CROWDING_SCORE", extractPlaceScore(candidate, "CROWDING_SCORE"));

		// ── 이동 경고 — FAIL 은 아니지만 감점한다 ────────────────────────────
		if (warnings.contains("STAIRS_PRESENT")) {
			total -= MOBILITY_WARNING_PENALTY;
		}
		if (warnings.contains("WALKING_OVER_LIMIT")) {
			total -= MOBILITY_WARNING_PENALTY;
		}
		// S15P21E201-540 — 접근성 미확인도 같은 폭으로 깎는다. 빼지는 않고 뒤로 민다.
		if (warnings.contains(ACCESSIBILITY_UNVERIFIED_WARNING)) {
			total -= MOBILITY_WARNING_PENALTY;
		}
		total = Math.max(0.0, total);

		// 🔴 미확인 제약을 제외할지 말지는 여기서 정하지 않는다.
		//
		// 한 번 여기서 정하게 만들었다가 되돌렸다(2026-09-05). REQUIRED 등급 미확인 사실이
		// 있으면 preRankScore 를 null 로 둬서, 설정과 무관하게 그 후보가 결과에서 빠지게
		// 했었다. "설정 한 줄로 안전이 무너지면 안 된다" 는 생각이었는데 전제가 틀렸다 —
		// gabolle.recommendation.unknown-exclusion-threshold 를 NONE 으로 바꾸는 것은
		// 사고가 아니라 **계획된 결정 경로**다. application.properties 주석이 그렇게 적어
		// 뒀고(FR-REC-02 는 "제외하지 않고 경고" 를 요구한다), S15P21E201-539 의 완료
		// 기준도 "확인 안 된 항목이 제외가 아니라 경고로 나온다" 이다. 여기서 점수를 지우면
		// 그 레버가 동작하지 않으면서 동작하는 것처럼 보인다.
		//
		// 그래서 채점기는 사실만 보고한다 — 무엇이 미확인이고 등급이 무엇인지를
		// unknownFacts 에 남기고, 제외 여부는 그 값을 아는 CandidateAssembler 가 설정
		// 임계값과 견줘 정한다. 기본값이 REQUIRED 라 지금 동작은 바뀌지 않는다.
		Double preRankScore = Double.valueOf(total);

		return new EngineCandidate(candidate.placeId(), CANDIDATE_SOURCE, verdict, violations, unknownFacts,
				null, featureValues, scoreComponents, preRankScore, reasonCodes, warnings);
	}

	/**
	 * <b>제약 판정만</b> 하고 점수는 매기지 않는다 (S15P21E201-555).
	 *
	 * <p>Editor's Pick 기준선이 쓴다. Pick 은 순서를 사람이 정했으므로 점수가 필요 없고,
	 * 필요한 것은 <b>이 장소가 이 사용자의 제약을 어기는가</b> 하나다.
	 *
	 * <p>🔴 <b>왜 {@link #score} 를 부르지 않는가.</b> 점수를 함께 계산하면 거리 성분을
	 * 위해 {@code distanceM} 이 필요하고, Pick 에는 출발지 기준 거리라는 것이 없다. 거기에
	 * 0 을 넣으면 "출발지에 붙어 있다" 는 뜻이 되고, 그 값이 {@code feature_values} 에
	 * 그대로 기록돼 나중에 거리 분포를 재는 질의를 오염시킨다. 안 쓰는 값을 지어내지 않기
	 * 위해 판정만 떼어 부른다.
	 *
	 * <p>🔴 판정 자체는 {@link #evaluateConstraints} 를 그대로 쓴다 — 같은 것을 두 번
	 * 구현하면 한쪽만 고쳐지는 날이 오고, 그 한쪽이 알레르기 필터다.
	 *
	 * @param reasonCodes 이 후보에 붙일 이유 코드 (Pick 이면 {@code EDITORIAL_PICK})
	 * @param extraWarnings 부르는 쪽이 이미 아는 경고. 판정으로 나온 경고와 합쳐진다
	 * @return {@code preRankScore}·{@code featureValues}·{@code scoreComponents} 가 비어 있는
	 *     후보. 비어 있는 것이 사실이다 — 우리는 점수를 매기지 않았다
	 */
	public EngineCandidate evaluateWithoutScoring(PlaceCandidateResponse.Candidate candidate,
			List<TripConstraint> constraints, List<UserPlaceCodeMap> constraintCodeMap, String candidateSource,
			List<String> reasonCodes, List<String> extraWarnings) {

		List<Map<String, Object>> violations = new ArrayList<>();
		List<Map<String, Object>> unknownFacts = new ArrayList<>();
		List<String> warnings = new ArrayList<>((extraWarnings == null) ? List.of() : extraWarnings);

		evaluateConstraints(candidate, constraints, constraintCodeMap, violations, unknownFacts, warnings);

		ConstraintVerdict verdict;
		if (!violations.isEmpty()) {
			verdict = ConstraintVerdict.FAIL;
		}
		else if (!unknownFacts.isEmpty()) {
			verdict = ConstraintVerdict.UNKNOWN;
		}
		else {
			verdict = ConstraintVerdict.PASS;
		}

		return new EngineCandidate(candidate.placeId(), candidateSource, verdict, violations, unknownFacts,
				null, Map.of(), Map.of(), null,
				(reasonCodes == null) ? List.of() : List.copyOf(reasonCodes), warnings);
	}

	// ══════════════════════════════════════════════════════════════════════
	// 제약 판정 — 판정표 그대로
	// ══════════════════════════════════════════════════════════════════════

	private void evaluateConstraints(PlaceCandidateResponse.Candidate candidate, List<TripConstraint> constraints,
			List<UserPlaceCodeMap> constraintCodeMap, List<Map<String, Object>> violations,
			List<Map<String, Object>> unknownFacts, List<String> warnings) {

		if (constraints == null) {
			return;
		}
		for (TripConstraint constraint : constraints) {
			// 🔴 SELECTED 가 아니면(NONE=없다고 답함, UNKNOWN=안 물어봄) 대조할 값 자체가 없다.
			if (constraint.answerStatus() != TripConstraint.AnswerStatus.SELECTED) {
				continue;
			}
			String type = constraint.type() == null ? "" : constraint.type().toUpperCase(Locale.ROOT);
			switch (type) {
				case "ALLERGY" -> evaluateAllergy(candidate, constraint, constraintCodeMap, violations, unknownFacts);
				case "DIET" -> evaluateDiet(candidate, constraint, constraintCodeMap, violations, unknownFacts);
				case "MOBILITY" ->
						evaluateMobility(candidate, constraint, constraintCodeMap, violations, unknownFacts, warnings);
				default -> {
					// 알려지지 않은 제약 종류 — 이 엔진이 아는 셋(ALLERGY/DIET/MOBILITY) 밖이면
					// 판정할 근거가 없다. 조용히 건너뛴다(DB CHECK 가 애초에 이 셋만 허용한다).
				}
			}
		}
	}

	private void evaluateAllergy(PlaceCandidateResponse.Candidate candidate, TripConstraint constraint,
			List<UserPlaceCodeMap> constraintCodeMap, List<Map<String, Object>> violations,
			List<Map<String, Object>> unknownFacts) {

		String featureType = hardFilterFeatureType(constraintCodeMap, "ALLERGY").orElse(null);
		if (featureType == null) {
			// 🔴 여기서 그냥 return 하면 안 된다. 대조표에 줄이 없는 것은 "판정할 필요가 없다" 가
			//    아니라 "판정할 수 없다" 이고, 안전 제약에서 그 둘을 같게 다루면 땅콩이 들었는지
			//    아무도 모르는 식당이 통과한다. 모른다는 사실을 그대로 남긴다 — 제약 자신의
			//    severity 가 REQUIRED 라 아래에서 preRankScore 가 null 이 되고 후보에서 빠진다.
			unknownFacts.add(Map.of("fact", "ALLERGEN_MAPPING_MISSING", "featureKey",
					constraint.constraintKey(), "severity", severityOf(constraint)));
			return;
		}
		String code = constraint.constraintKey();
		switch (bucketFor(candidate, featureType, code)) {
			case PRESENT -> violations.add(Map.of("code", "ALLERGEN_PRESENT", "featureKey", code));
			case UNVERIFIED -> unknownFacts.add(Map.of("fact", "ALLERGEN_UNVERIFIED", "featureKey", code,
					"severity", severityOf(constraint)));
			case ABSENT -> {
				// 확인된 해당 없음 — 통과 기여. 추가로 할 일이 없다.
			}
		}
	}

	private void evaluateDiet(PlaceCandidateResponse.Candidate candidate, TripConstraint constraint,
			List<UserPlaceCodeMap> constraintCodeMap, List<Map<String, Object>> violations,
			List<Map<String, Object>> unknownFacts) {

		String featureType = hardFilterFeatureType(constraintCodeMap, "DIET").orElse(null);
		if (featureType == null) {
			// 🔴 알레르기와 같은 이유로 조용히 넘어가지 않는다. 필수(REQUIRED) 식단이면
			//    severityOf 가 REQUIRED 를 주고 그 후보는 결과에서 빠진다.
			unknownFacts.add(Map.of("fact", "DIET_MAPPING_MISSING", "featureKey",
					constraint.constraintKey(), "severity", severityOf(constraint)));
			return;
		}
		String code = constraint.constraintKey();
		PresenceBucket bucket = bucketFor(candidate, featureType, code);
		boolean required = constraint.dietRequirement() == TripConstraint.DietRequirement.REQUIRED;

		if (required) {
			switch (bucket) {
				case PRESENT -> {
					// 지원 표식이 있다고 확인됨 — 통과 기여.
				}
				case ABSENT -> violations.add(Map.of("code", "DIET_NOT_SUPPORTED", "featureKey", code));
				case UNVERIFIED -> unknownFacts.add(Map.of("fact", "DIET_SUPPORT_UNVERIFIED", "featureKey", code,
						"severity", severityOf(constraint)));
			}
		}
		else if (bucket == PresenceBucket.UNVERIFIED) {
			// PREFERRED 는 미확인일 때만 흔적을 남긴다 — FAIL 은 없다(소프트 선호이기 때문).
			unknownFacts.add(Map.of("fact", "DIET_SUPPORT_UNVERIFIED", "featureKey", code,
					"severity", severityOf(constraint)));
		}
	}

	/**
	 * 이동 제약(휠체어·유아차·무거운 짐·계단 회피·보행 상한)을 본다.
	 *
	 * <h2>🔴 S15P21E201-540 — "안 재 봤다" 를 탈락으로 세지 않는다</h2>
	 *
	 * 예전에는 접근성 표식이 <b>없는</b> 장소를 {@code unknownFacts} 에 REQUIRED 등급으로
	 * 넣었고, 기본 설정({@code gabolle.recommendation.unknown-exclusion-threshold=REQUIRED})이
	 * 그 후보를 결과에서 뺐다. 표식이 있는 것과 없는 것을 <b>같게 다룬 것</b>이다.
	 *
	 * <p>그 전제가 데이터와 맞지 않는다. 2026-09-16 운영 실측으로 접근성 표식이 붙은 장소는
	 * 전체 2,683곳 중 <b>102곳(4%)</b>이다. 그래서 휠체어 조건을 켜면 96%가 사라지고, 반경
	 * 조건까지 겹치면 <b>후보가 0건</b>이 된다. 실제로 그날까지 쌓인 추천 실패 18건 중
	 * <b>8건</b>이 전부 이 자리({@code CONSTRAINT_EVALUATION} 단계)에서 죽었다.
	 *
	 * <p>그래서 둘을 가른다.
	 *
	 * <table border="1">
	 * <caption>접근성 표식에 따른 판정</caption>
	 * <tr><th>표식</th><th>뜻</th><th>판정</th></tr>
	 * <tr><td>PRESENT</td><td>재 봤고 갈 수 있다</td><td>통과</td></tr>
	 * <tr><td>ABSENT</td><td><b>재 봤고 못 간다</b></td><td>여전히 탈락 — 데이터가 없는 게 아니라 있는 것이다</td></tr>
	 * <tr><td>UNVERIFIED</td><td>안 재 봤다</td><td><b>경고 + 감점.</b> 빼지 않고 뒤로 민다</td></tr>
	 * </table>
	 *
	 * <p>이것은 같은 함수 위쪽의 {@code STAIRS_PRESENT} · {@code WALKING_OVER_LIMIT} 이 이미
	 * 하고 있는 처리와 같다 — 이 자리만 다르게 돼 있었다.
	 *
	 * <h2>전역 설정을 내리지 않은 이유</h2>
	 *
	 * {@code unknown-exclusion-threshold} 를 {@code NONE} 으로 두면 이 문제는 풀리지만
	 * <b>알레르기 미확인까지 같이 풀린다.</b> 그 enum 의 주석이 왜 안 되는지 적어 뒀다 —
	 * 사용자가 식당에 전화해 땅콩기름을 쓰는지 확인할 수는 없다. 다이얼은 눈금이 하나뿐이라
	 * 안전 제약과 편의 제약을 못 가른다. 그래서 다이얼이 아니라 이 자리를 고친다.
	 *
	 * <h2>화면이 알아야 하는 것</h2>
	 *
	 * 감점된 후보에는 {@link #ACCESSIBILITY_UNVERIFIED_WARNING} 이 붙어 응답까지 간다.
	 * 「휠체어로 갈 수 있음」은 지킬 수 없는 약속이므로(경사가 완만해도 입구에 계단 세 칸이면
	 * 못 간다) 화면은 <b>잰 것을 그대로</b> 말해야 한다 — 이 경고가 그 재료다.
	 *
	 * <p>🔴 <b>정정 (2026-09-17, S15P21E201-1158) — 위 줄의 "응답까지 간다" 는 절반만 참이었다.</b>
	 *
	 * <ul>
	 * <li><b>추천 결과</b>({@code GET /api/v1/recommendation-jobs/{jobId}})로는 <b>가고 있었다.</b>
	 * {@code RecommendationResultQueryService.mobilityWarnings} 의 {@code code.startsWith("ACCESS")}
	 * 에 걸려 항목마다 {@code mobilityWarnings} 로, 그리고 최상위 {@code conflicts} 로 나간다.</li>
	 * <li><b>일정 상세</b>({@code ItineraryDetailResponse})로는 <b>안 갔다.</b> 값이
	 * {@code itinerary_item.warning_codes} 에 저장까지 되는데 응답 DTO 에 담는 칸이 없었다.
	 * 「화면이 알아야 하는 것」이라고 적어 두고 마지막 한 칸이 안 이어져 있었다.</li>
	 * </ul>
	 *
	 * <p>이 티켓이 뒤쪽을 이었다. 옛 문장을 지우지 않는 이유는, 그 문장을 믿고 <b>「이미 나간다」로
	 * 읽은 사람이 실제로 있었기 때문</b>이다 — 무엇이 가고 무엇이 안 갔는지를 함께 남긴다.
	 */
	private void evaluateMobility(PlaceCandidateResponse.Candidate candidate, TripConstraint constraint,
			List<UserPlaceCodeMap> constraintCodeMap, List<Map<String, Object>> violations,
			List<Map<String, Object>> unknownFacts, List<String> warnings) {

		String key = constraint.constraintKey();

		if ("STAIRS_AVOIDANCE".equals(key)) {
			String featureType = flagCompareFeatureType(constraintCodeMap, "MOBILITY").orElse(null);
			if (featureType != null && bucketFor(candidate, featureType, null) == PresenceBucket.PRESENT) {
				// 🔴 FAIL 이 아니다 — 경고 + 감점만 한다(판정표).
				warnings.add("STAIRS_PRESENT");
			}
			return;
		}
		if ("MAX_WALKING_METERS".equals(key)) {
			if (constraint.threshold() != null && candidate.distanceM() > constraint.threshold()) {
				// 🔴 FAIL 이 아니다 — 경고 + 감점만 한다.
				warnings.add("WALKING_OVER_LIMIT");
			}
			return;
		}

		// 그 밖의 이동 조건(WHEELCHAIR·STROLLER·HEAVY_LUGGAGE 등) — ACCESSIBILITY_TAG:<key>.
		String featureType = hardFilterFeatureType(constraintCodeMap, "MOBILITY").orElse(null);
		if (featureType == null) {
			unknownFacts.add(Map.of("fact", "ACCESSIBILITY_MAPPING_MISSING", "featureKey", key,
					"severity", severityOf(constraint)));
			return;
		}
		switch (bucketFor(candidate, featureType, key)) {
			// 🔴 방향이 알레르기와 반대다 — 여기는 "없다고 확인됨" 이 FAIL 이다.
			//    재 보고 안 된다고 나온 곳은 그대로 뺀다. 그건 데이터가 없는 게 아니라 있는 것이다.
			case ABSENT -> violations.add(Map.of("code", "ACCESS_VERIFIED_UNAVAILABLE", "featureKey", key));
			// 🔴 S15P21E201-540 — "안 재 봤다" 는 FAIL 이 아니다. 경고 + 감점이다.
			//    같은 함수 위쪽의 STAIRS_PRESENT · WALKING_OVER_LIMIT 과 같은 처리다.
			case UNVERIFIED -> warnings.add(ACCESSIBILITY_UNVERIFIED_WARNING);
			case PRESENT -> {
				// 검증된 접근 가능 — 통과 기여.
			}
		}
	}

	/** 🔴 severity 를 지어내지 않는 유일한 통로. HARD → REQUIRED, SOFT → PREFERRED. */
	private static String severityOf(TripConstraint constraint) {
		return constraint.severity() == TripConstraint.Severity.HARD ? "REQUIRED" : "PREFERRED";
	}

	private enum PresenceBucket {
		/** 있다고 확인됨. */
		PRESENT,
		/** 확인된 해당 없음(VERIFIED/ESTIMATED + 값 false). */
		ABSENT,
		/** 표식 행이 없거나 evidenceStatus=UNKNOWN. */
		UNVERIFIED
	}

	private PresenceBucket bucketFor(PlaceCandidateResponse.Candidate candidate, String featureType,
			String featureKey) {
		PlaceFeatureView row = findFeature(candidate, featureType, featureKey);
		String status = (row == null) ? "UNKNOWN" : row.evidenceStatus();
		String raw = (row == null) ? null : rawValue(row);
		if (FeaturePresence.indicatesPresence(status, raw)) {
			return PresenceBucket.PRESENT;
		}
		if (!FeaturePresence.cannotRuleOutPresence(status, raw)) {
			return PresenceBucket.ABSENT;
		}
		return PresenceBucket.UNVERIFIED;
	}

	private PlaceFeatureView findFeature(PlaceCandidateResponse.Candidate candidate, String featureType,
			String featureKey) {
		for (PlaceFeatureView feature : candidate.features()) {
			if (feature.featureType().equals(featureType) && Objects.equals(feature.featureKey(), featureKey)) {
				return feature;
			}
		}
		return null;
	}

	private String rawValue(PlaceFeatureView feature) {
		return (feature.value() == null) ? null : feature.value().toString();
	}

	private Optional<String> hardFilterFeatureType(List<UserPlaceCodeMap> rows, String constraintType) {
		return matchKindFeatureType(rows, constraintType, MatchKind.HARD_FILTER);
	}

	private Optional<String> flagCompareFeatureType(List<UserPlaceCodeMap> rows, String constraintType) {
		return matchKindFeatureType(rows, constraintType, MatchKind.FLAG_COMPARE);
	}

	private Optional<String> matchKindFeatureType(List<UserPlaceCodeMap> rows, String userInputCode,
			MatchKind matchKind) {
		if (rows == null) {
			return Optional.empty();
		}
		return rows.stream()
				.filter(row -> userInputCode.equals(row.getUserInputCode()) && row.getMatchKind() == matchKind)
				.map(UserPlaceCodeMap::getPlaceFeatureType)
				.findFirst();
	}

	private Optional<String> featureTypeFor(List<UserPlaceCodeMap> rows, String preferenceCode) {
		if (rows == null) {
			return Optional.empty();
		}
		return rows.stream()
				.filter(row -> preferenceCode.equals(row.getUserInputCode()))
				.map(UserPlaceCodeMap::getPlaceFeatureType)
				.findFirst();
	}

	// ══════════════════════════════════════════════════════════════════════
	// 점수 — 태그 겹침 · 점수형 선호 정렬
	// ══════════════════════════════════════════════════════════════════════

	/**
	 * 접힌 취향 벡터가 {@code CATEGORY} 겹침에 더하는 덧점수 — S15P21E201-943.
	 *
	 * <h2>🔴 기존 채점을 바꾸지 않는다. 더하기만 한다</h2>
	 *
	 * {@code PreferenceSnapshot} 기반 채점은 이미 돌고 있고 발표가 그것으로 돈다. 벡터를
	 * <b>대신</b> 쓰게 바꾸면 ① 아직 벡터가 없는 사람(지금 대부분)이 갑자기 취향 반영 0 이 되거나
	 * ② 접기 배치의 결함이 그대로 추천을 망가뜨린다. <b>있으면 더하고 없으면 지금과 완전히 같다</b>
	 * 로 두면 위험이 한쪽으로만 간다.
	 *
	 * <h2>겹침 비율을 그대로 흉내 낸다 — 다만 가중치로 잰다</h2>
	 *
	 * {@link #applyTagComponent} 는 <b>맞은 개수 ÷ 고른 개수</b>다. 여기서는 <b>맞은 성분의
	 * 가중치 합 ÷ 벡터의 CATEGORY 성분 개수</b>를 쓴다. 두 가지가 따라온다.
	 * <ul>
	 * <li>전부 맞고 가중치가 1.0 이면 1.0 — 기존 비율과 같은 축이다</li>
	 * <li>가중치가 <b>음수</b>면(싫어하는 갈래) 총점이 <b>내려간다.</b> 개수만 세면 못 하는 일이고,
	 *     벡터를 쓰는 이유의 절반이 이것이다</li>
	 * </ul>
	 *
	 * <h2>🔴 지금 이 항이 맞출 수 있는 낱말은 사실상 하나다</h2>
	 *
	 * 온보딩 취향 여섯 중 장소에 실제로 붙는 것은 {@code FOOD}(모든 장소 — 그래서 변별력이 없다)와
	 * {@code CAFE_HEALING} 둘뿐이다. 나머지 넷({@code CITY}·{@code CULTURE_TEMPLE}·
	 * {@code NATURE_WALK}·{@code SEA_BEACH})은 붙는 장소가 없다 — S15P21E201-1108.
	 * 이 항의 효과가 작아 보인다면 배수가 아니라 <b>그쪽</b>을 먼저 본다.
	 *
	 * @param tasteWeights 이 사용자의 현재 판 성분 전부. 요청당 한 번 읽어서 넘어온다 —
	 *     후보마다 다시 읽으면 "질의 개수가 후보 수에 비례하면 안 된다" 를 어긴다
	 */
	private double applyTasteVectorComponent(PlaceCandidateResponse.Candidate candidate,
			List<UserPlaceCodeMap> preferenceCodeMap, List<UserTasteWeight> tasteWeights,
			double multiplier, Map<String, Object> featureValues, Map<String, Object> scoreComponents,
			List<String> reasonCodes) {

		String featureType = featureTypeFor(preferenceCodeMap, "CATEGORY").orElse(null);
		List<UserTasteWeight> categoryWeights = (tasteWeights == null) ? List.of()
				: tasteWeights.stream().filter((w) -> w.getDimension() == TasteDimension.CATEGORY).toList();

		if (featureType == null || categoryWeights.isEmpty()) {
			// 🔴 벡터가 없는 사람이 지금 대부분이다. 그때는 이 항이 아예 없었던 것과 같아야 한다 —
			//    값을 0.0 으로 적지 않고 null 로 둔다("겹친 게 없다" 와 "잴 것이 없다" 는 다르다).
			featureValues.put("tasteVectorOverlap", null);
			scoreComponents.put("tasteVectorContribution", componentDetail(multiplier, null, null));
			return 0.0;
		}

		Set<String> placeTags = new LinkedHashSet<>();
		for (PlaceFeatureView feature : candidate.features()) {
			if (featureType.equals(feature.featureType()) && feature.featureKey() != null
					&& FeaturePresence.indicatesPresence(feature.evidenceStatus(), rawValue(feature))) {
				placeTags.add(feature.featureKey());
			}
		}

		Map<String, Object> matched = new LinkedHashMap<>();
		double sum = 0.0;
		for (UserTasteWeight weight : categoryWeights) {
			if (placeTags.contains(weight.getCode())) {
				matched.put(weight.getCode(), weight.getWeight());
				sum += weight.getWeight();
			}
		}
		double ratio = sum / categoryWeights.size();

		featureValues.put("tasteVectorOverlap", ratio);
		// 🔴 evidence 를 함께 남긴다. 지금은 전부 SURVEY 라 이 항이 설문을 두 번 세는 중인데,
		//    그 사실을 나중에 되짚으려면 무엇을 근거로 더했는지가 행에 남아 있어야 한다.
		scoreComponents.put("tasteVectorContribution", componentDetail(multiplier, ratio,
				Map.of("matched", matched, "componentCount", categoryWeights.size(),
						"evidence", evidenceSummary(categoryWeights))));
		if (!matched.isEmpty()) {
			reasonCodes.add("TASTE_VECTOR_MATCH");
		}
		return multiplier * ratio;
	}

	/** 성분들이 무엇을 근거로 접혔는지 — {@code SURVEY} · {@code INTERACTION} · {@code BLENDED} 별 개수. */
	private static Map<String, Integer> evidenceSummary(List<UserTasteWeight> weights) {
		Map<String, Integer> counts = new LinkedHashMap<>();
		for (UserTasteWeight weight : weights) {
			counts.merge(weight.getEvidence().name(), 1, Integer::sum);
		}
		return counts;
	}

	private double applyTagComponent(PlaceCandidateResponse.Candidate candidate, PreferenceSnapshot preferenceSnapshot,
			List<UserPlaceCodeMap> preferenceCodeMap, String preferenceCode,
			double weight, String componentKey, String reasonCode, String featureValueKey,
			Map<String, Object> featureValues, Map<String, Object> scoreComponents, List<String> reasonCodes) {

		// 🔴 검색할 장소 표식 유형도 대조표에서 읽은 featureType 을 그대로 쓴다 — 별도로
		// 문자열을 하드코딩하면 대조표가 바뀌어도 이 검색은 안 따라간다.
		String featureType = featureTypeFor(preferenceCodeMap, preferenceCode).orElse(null);
		List<String> userCodes = (featureType == null) ? List.of()
				: PreferenceJson.codesFor(preferenceSnapshot, preferenceCode, this.objectMapper);

		if (featureType == null || userCodes.isEmpty()) {
			// 대조표가 없거나 사용자가 이 차원을 안 골랐다 — 비교 대상이 없으니 못 구한 피처다.
			featureValues.put(featureValueKey, null);
			scoreComponents.put(componentKey, componentDetail(weight, null, null));
			return 0.0;
		}

		Set<String> placeTags = new LinkedHashSet<>();
		for (PlaceFeatureView feature : candidate.features()) {
			if (featureType.equals(feature.featureType()) && feature.featureKey() != null
					&& FeaturePresence.indicatesPresence(feature.evidenceStatus(), rawValue(feature))) {
				placeTags.add(feature.featureKey());
			}
		}

		List<String> matched = new ArrayList<>();
		for (String code : userCodes) {
			if (placeTags.contains(code)) {
				matched.add(code);
			}
		}
		double ratio = matched.size() / (double) userCodes.size();

		featureValues.put(featureValueKey, ratio);
		scoreComponents.put(componentKey, componentDetail(weight, ratio, Map.of("matched", matched)));
		if (!matched.isEmpty()) {
			reasonCodes.add(reasonCode);
		}
		return weight * ratio;
	}

	private void applyAlignmentDimension(PlaceCandidateResponse.Candidate candidate,
			PreferenceSnapshot preferenceSnapshot, List<UserPlaceCodeMap> preferenceCodeMap, String preferenceCode,
			String featureValueKey, boolean placeValueIsPercent, Map<String, Object> featureValues,
			Map<String, Double> alignments, List<String> reasonCodes) {

		String featureType = featureTypeFor(preferenceCodeMap, preferenceCode).orElse(null);
		Double placeScore = (featureType == null) ? null : extractPlaceScore(candidate, featureType);
		featureValues.put(featureValueKey, placeScore);

		if (placeScore == null) {
			return;
		}
		Double prefScore = PreferenceJson.scoreFor(preferenceSnapshot, preferenceCode, this.objectMapper);
		if (prefScore == null) {
			return;
		}
		double normalizedPlace = placeValueIsPercent ? placeScore / 100.0 : placeScore;
		double alignment = clamp01(1.0 - Math.abs(normalizedPlace - prefScore));
		alignments.put(preferenceCode, alignment);
		reasonCodes.add("PREF_ALIGNED_" + preferenceCode);
	}

	/** 점수형 피처(키가 없는 행) 하나의 값. 못 구하면 {@code null} 이다. */
	private Double extractPlaceScore(PlaceCandidateResponse.Candidate candidate, String featureType) {
		PlaceFeatureView row = findFeature(candidate, featureType, null);
		if (row == null || "UNKNOWN".equals(row.evidenceStatus()) || row.value() == null) {
			return null;
		}
		if (row.value().isNumber()) {
			return row.value().asDouble();
		}
		var score = row.value().path("score");
		return score.isNumber() ? score.asDouble() : null;
	}

	private static Map<String, Object> componentDetail(double weight, Double value, Map<String, Object> extra) {
		Map<String, Object> detail = new LinkedHashMap<>();
		detail.put("weight", weight);
		detail.put("value", value);
		if (extra != null) {
			detail.putAll(extra);
		}
		return detail;
	}

	private static double clamp01(double value) {
		return Math.max(0.0, Math.min(1.0, value));
	}
}
