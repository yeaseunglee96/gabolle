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
import com.gabolle.backend.recommendation.config.BaselineEngineProperties;
import com.gabolle.backend.recommendation.domain.ConstraintVerdict;
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

	private final ObjectMapper objectMapper;

	public BaselineCandidateScorer(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	/**
	 * @param preferenceCodeMap {@code user_place_code_map} 의 {@code PREFERENCE} 행 전부
	 * @param constraintCodeMap {@code user_place_code_map} 의 {@code CONSTRAINT} 행 전부.
	 *     {@code MOBILITY} 는 {@code ACCESSIBILITY_TAG}(HARD_FILTER)·{@code STAIRS_PRESENT}
	 *     (FLAG_COMPARE) 둘에 걸리므로 코드가 아니라 이 목록에서 matchKind 로 가른다
	 */
	public EngineCandidate score(PlaceCandidateResponse.Candidate candidate,
			PreferenceSnapshot preferenceSnapshot, List<TripConstraint> constraints, int radiusM,
			BaselineEngineProperties.Weights weights,
			List<UserPlaceCodeMap> preferenceCodeMap, List<UserPlaceCodeMap> constraintCodeMap) {

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

		// ── 거리 — 항상 잴 수 있다 ────────────────────────────────────────────
		double distanceComponent = clamp01(1.0 - (candidate.distanceM() / (double) radiusM));
		featureValues.put("distanceM", candidate.distanceM());
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

		// ── 점수형 선호 다섯 — LOCALITY·QUIETNESS·TOURIST_PREFERENCE·SHADE_PREFERENCE·SLOPE_PREFERENCE
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

		Double alignmentAverage = alignments.isEmpty() ? null
				: alignments.values().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
		scoreComponents.put("preferenceAlignment",
				componentDetail(weights.preferenceAlignment(), alignmentAverage, Map.of("dimensions", alignments)));
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
		total = Math.max(0.0, total);

		// 🔴 두 번째 안전장치 — gabolle.recommendation.unknown-exclusion-threshold 가
		// REQUIRED 가 아닌 값(예: NONE)으로 바뀌어도, REQUIRED 등급 미확인 사실이 하나라도
		// 있으면 preRankScore 를 null 로 둔다. CandidateAssembler 의 "점수 없는 후보에는
		// 순위를 붙이지 않는다" 불변식이 설정과 무관하게 이 후보를 걸러 낸다 — 장치 둘이
		// 같은 것(미확인 안전 제약이 새어 나가는 것)을 막는다.
		boolean hasRequiredUnknown = unknownFacts.stream()
				.anyMatch(fact -> "REQUIRED".equals(fact.get("severity")));
		Double preRankScore = hasRequiredUnknown ? null : Double.valueOf(total);

		return new EngineCandidate(candidate.placeId(), CANDIDATE_SOURCE, verdict, violations, unknownFacts,
				null, featureValues, scoreComponents, preRankScore, reasonCodes, warnings);
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
			case ABSENT -> violations.add(Map.of("code", "ACCESS_VERIFIED_UNAVAILABLE", "featureKey", key));
			case UNVERIFIED -> unknownFacts.add(Map.of("fact", "ACCESSIBILITY_UNVERIFIED", "featureKey", key,
					"severity", severityOf(constraint)));
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
