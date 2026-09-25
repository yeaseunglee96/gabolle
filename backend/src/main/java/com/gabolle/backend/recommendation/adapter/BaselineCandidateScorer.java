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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.gabolle.backend.place.api.PlaceCandidateResponse;
import com.gabolle.backend.place.api.PlaceFeatureView;
import com.gabolle.backend.place.domain.FeaturePresence;
import com.gabolle.backend.place.domain.MatchKind;
import com.gabolle.backend.place.domain.UserPlaceCodeMap;
import com.gabolle.backend.preference.application.PreferenceJson;
import com.gabolle.backend.recommendation.application.RecommendationCodes;
import com.gabolle.backend.preference.domain.TasteDimension;
import com.gabolle.backend.preference.domain.TasteEvidence;
import com.gabolle.backend.preference.domain.TasteWeightComponent;
import com.gabolle.backend.recommendation.config.BaselineEngineProperties;
import com.gabolle.backend.recommendation.config.PreferenceAlignmentWeights;
import com.gabolle.backend.recommendation.domain.CoarseArea;
import com.gabolle.backend.recommendation.domain.ConstraintVerdict;
import com.gabolle.backend.recommendation.domain.DistanceBucket;
import com.gabolle.backend.trip.domain.PreferenceSnapshot;
import com.gabolle.backend.trip.domain.TripConstraint;

import tools.jackson.databind.ObjectMapper;

/**
 * {@link PlaceCandidateResponse.Candidate} 하나를 {@link EngineCandidate} 하나로 만든다.
 *
 * <p>이 클래스는 DB 를 모른다. 사용자 입력 코드 ↔ 장소 표식 유형 대조는
 * {@link BaselineRecommendationEngine} 이 배치당 한 번 읽어 {@link UserPlaceCodeMap} 목록으로
 * 넘긴다 — 후보마다 다시 질의하면 질의 개수가 후보 수에 비례하게 된다.
 *
 * <p>severity 는 {@link #severityOf(TripConstraint)} 가 유일한 통로다 — {@code HARD} 는 항상
 * {@code REQUIRED}, {@code SOFT} 는 항상 {@code PREFERRED}.
 */
@Component
public class BaselineCandidateScorer {

	/** {@code EngineCandidate.candidateSource} — 어디서 왔는지 알 수 있는 값. */
	static final String CANDIDATE_SOURCE = "BASELINE_PLACE_QUERY";

	/**
	 * 이동 경고(계단·보행 상한 초과) 하나당 깎는 점수. 판정표가 폭을 정하지 않았고 데이터가
	 * 쌓이면 조정될 값이라 설정이 아니라 상수로 뒀다.
	 */
	private static final double MOBILITY_WARNING_PENALTY = 0.05;

	/**
	 * 접근성을 안 재 봤다는 경고. 문자열을 여기서 다시 적지 않는다 — 세는 쪽이 다른 갈래에
	 * 있어서 두 벌이 되면 한쪽만 고쳐지는 날 경고가 조용히 0건이 된다.
	 */
	static final String ACCESSIBILITY_UNVERIFIED_WARNING = RecommendationCodes.WARNING_ACCESSIBILITY_UNVERIFIED;

	/** 알레르기 재료가 들었는지 안 재 봤다는 경고(S15P21E201-1633). 같은 이유로 문자열을 여기서 다시 적지 않는다. */
	static final String ALLERGEN_UNVERIFIED_WARNING = RecommendationCodes.WARNING_ALLERGEN_UNVERIFIED;

	/** 식단을 안 재 봤다는 경고. 같은 이유로 문자열을 여기서 다시 적지 않는다. */
	static final String DIET_UNVERIFIED_WARNING = RecommendationCodes.WARNING_DIET_SUPPORT_UNVERIFIED;

	/**
	 * 경사가 이동 조건(휠체어·유아차·큰 짐)의 상한을 넘었다 — S15P21E201-1625. 「반드시」면 탈락 사유 코드,
	 * 「되도록」이면 경고 코드로 같은 낱말을 쓴다.
	 */
	static final String SLOPE_OVER_LIMIT = "SLOPE_OVER_LIMIT";

	/**
	 * 이동 조건이 받아들이는 경사 상한(%)의 기본값 — 온톨로지의 휠체어 경사로 기준 1:12({@code bm:RuleWheelchairSlope}).
	 * 휠체어·유아차·큰 짐이 이 값 하나를 같이 쓴다(2026-09-25 사용자 결정 — 「경사」 하나로 묶는다).
	 */
	static final double DEFAULT_MAX_SLOPE_PERCENT = 8.33;

	private final ObjectMapper objectMapper;

	private final double maxSlopePercent;

	public BaselineCandidateScorer(ObjectMapper objectMapper) {
		this(objectMapper, DEFAULT_MAX_SLOPE_PERCENT);
	}

	@Autowired
	public BaselineCandidateScorer(ObjectMapper objectMapper,
			@Value("${gabolle.recommendation.mobility.max-slope-percent:" + DEFAULT_MAX_SLOPE_PERCENT + "}")
			double maxSlopePercent) {
		this.objectMapper = objectMapper;
		this.maxSlopePercent = maxSlopePercent;
	}

	/** 사용자가 고른 여행 테마(갈래) — 채점의 관심 항이 읽는 것과 같은 답이다. 안 골랐으면 빈 목록. */
	List<String> chosenCategories(PreferenceSnapshot preferenceSnapshot) {
		return PreferenceJson.codesFor(preferenceSnapshot, "CATEGORY", this.objectMapper);
	}

	/** 태그로 겹침을 재는 취향 — {@link #score} 의 분위기·음식 항과 같은 목록이다. */
	private static final List<String> TAG_TASTES = List.of("ATMOSPHERE", "FOOD_PREFERENCE");

	/** 점수로 맞춰 보는 취향 — {@link #score} 의 점수형 다섯과 같은 목록이다. */
	private static final List<String> SCORE_TASTES = List.of("LOCALITY", "QUIETNESS", "TOURIST_PREFERENCE",
			"SHADE_PREFERENCE", "SLOPE_PREFERENCE");

	/**
	 * 테마 말고도 채점에 쓰일 취향 답이 있는가 (S15P21E201-1639). 채점이 읽는 그대로 읽는다 — 「상관없어요」(경사 ALLOW ·
	 * 그늘 NO_PREFERENCE)나 빈 음식 목록은 값이 없어 순위를 가르지 못하므로 거짓이다. 씀씀이는 취향 항이 아니다.
	 */
	boolean hasTasteBeyondCategory(PreferenceSnapshot preferenceSnapshot) {
		for (String dimension : TAG_TASTES) {
			if (!PreferenceJson.codesFor(preferenceSnapshot, dimension, this.objectMapper).isEmpty()) {
				return true;
			}
		}
		for (String dimension : SCORE_TASTES) {
			if (PreferenceJson.scoreFor(preferenceSnapshot, dimension, this.objectMapper) != null) {
				return true;
			}
		}
		return false;
	}

	/**
	 * @param alignmentWeights 점수형 취향 다섯 차원이 {@code weights.preferenceAlignment} 를
	 *     나누는 비율. 빈 주입이 아니라 인수인 이유는, 한 요청 안에서 설정이 다른 두 벌로 같은
	 *     후보를 채점해 견주는 벤치마크가 생성자 주입이면 불가능하기 때문이다
	 * @param preferenceCodeMap {@code user_place_code_map} 의 {@code PREFERENCE} 행 전부
	 * @param constraintCodeMap {@code user_place_code_map} 의 {@code CONSTRAINT} 행 전부.
	 *     {@code MOBILITY} 는 {@code ACCESSIBILITY_TAG}(HARD_FILTER)·{@code STAIRS_PRESENT}
	 *     (FLAG_COMPARE) 둘에 걸리므로 코드가 아니라 이 목록에서 matchKind 로 가른다
	 */
	public EngineCandidate score(PlaceCandidateResponse.Candidate candidate,
			PreferenceSnapshot preferenceSnapshot, List<TripConstraint> constraints, int radiusM,
			BaselineEngineProperties.Weights weights, PreferenceAlignmentWeights alignmentWeights,
			List<UserPlaceCodeMap> preferenceCodeMap, List<UserPlaceCodeMap> constraintCodeMap,
			List<TasteWeightComponent> tasteWeights, double tasteVectorMultiplier) {

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

		// 다양성 재정렬이 쓸 값. 점수에 쓰지 않고 "같은 종류인가 · 같은 동네인가" 판단에만
		// 쓰인다. 여기 남기는 이유는 CandidateAssembler 가 후보 객체만 보고 그 판단을 할 수
		// 있어야 하기 때문이다 — 장소 표를 다시 읽으면 질의 수가 후보 수에 비례한다.
		//
		// 좌표를 그대로 남기지 않는다. feature_values 는 일반 추천 로그라 정밀 좌표를 남기지
		// 않는다. localityBucket 은 소수점 두 자리에서 자른 정수 쌍이라 부산 위도에서 대략
		// 1km 칸이고, 되돌려도 그 칸보다 정밀한 위치가 나오지 않는다.
		featureValues.put("category", candidate.category());
		featureValues.put("localityBucket", CoarseArea.of(candidate.lat(), candidate.lng()));
		// 카테고리만으로는 돼지국밥집과 칼국수집이 둘 다 FOOD 라 재정렬이 둘을 구분하지 못한다.
		// 그래서 한 칸 더 가는 축을 같이 남긴다 (S15P21E201-1450).
		featureValues.put("cuisine", cuisineTagsOf(candidate, preferenceCodeMap));

		// ── 거리 — 항상 잴 수 있다 ────────────────────────────────────────────
		double distanceComponent = clamp01(1.0 - (candidate.distanceM() / (double) radiusM));
		featureValues.put("distanceM", candidate.distanceM());
		// 장기 분석은 띠로 센다. 미터만 남기면 질의마다 경계를 다시 정하게 되어 같은 지표가
		// 사람마다 다른 숫자가 된다.
		DistanceBucket distanceBucket = DistanceBucket.of(candidate.distanceM());
		featureValues.put("distanceBucket", distanceBucket);
		scoreComponents.put("distance", componentDetail(weights.distance(), distanceComponent, null));
		// 「출발지에서 가까움」은 걸어갈 만한 곳(띠의 「걷기 상한 안쪽」 — 1km 미만)에만 붙인다 (S15P21E201-1638).
		// 전에는 거리와 상관없이 모든 후보에 붙어 4.7km 떨어진 곳도 「가까움」이었다. 점수는 그대로다.
		if (distanceBucket == DistanceBucket.UNDER_500M || distanceBucket == DistanceBucket.M500_TO_1KM) {
			reasonCodes.add("NEAR_ORIGIN");
		}
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

		// ── 접힌 취향 벡터의 덧점수 ──────────────────────────────────────────
		total += applyTasteVectorComponent(candidate, preferenceCodeMap, tasteWeights, tasteVectorMultiplier,
				featureValues, scoreComponents, reasonCodes);

		// ── 점수형 선호 다섯 — LOCALITY·QUIETNESS·TOURIST_PREFERENCE·SHADE_PREFERENCE·SLOPE_PREFERENCE
		//
		// 이 다섯은 weights.preferenceAlignment(기본 0.10) 하나를 나눠 쓴다. 단순 평균이 아니라
		// 가중 평균이다 — 단순 평균이면 가장 강하게 답한 축조차 총점에 0.10 ÷ 5 = 0.02 밖에
		// 기여하지 못해 거리(0.30)에 덮인다. 비율과 계산은 PreferenceAlignmentWeights 에 있다.
		Map<String, Double> alignments = new LinkedHashMap<>();
		applyAlignmentDimension(candidate, preferenceSnapshot, preferenceCodeMap, "LOCALITY", "localityScore",
				false, featureValues, alignments, reasonCodes);
		applyAlignmentDimension(candidate, preferenceSnapshot, preferenceCodeMap, "QUIETNESS", "quietnessScore",
				false, featureValues, alignments, reasonCodes);
		applyAlignmentDimension(candidate, preferenceSnapshot, preferenceCodeMap, "TOURIST_PREFERENCE",
				"touristRatio", false, featureValues, alignments, reasonCodes);
		applyAlignmentDimension(candidate, preferenceSnapshot, preferenceCodeMap, "SHADE_PREFERENCE", "shadeScore",
				false, featureValues, alignments, reasonCodes);
		// SLOPE_PERCENT 는 0~100 퍼센트이고 선호값은 0~1 스케일이다 — 비교 전에 100 으로 나눠
		// 같은 축으로 맞춘다.
		applyAlignmentDimension(candidate, preferenceSnapshot, preferenceCodeMap, "SLOPE_PREFERENCE", "slopePercent",
				true, featureValues, alignments, reasonCodes);

		Double alignmentAverage = alignmentWeights.weightedAverage(alignments);
		// dimensions 옆에 dimensionWeights 를 같이 남긴다. 정렬도만 남기면 "이 장소가 왜 이
		// 순위인가" 를 되짚을 때 어느 축이 얼마나 셌는지를 알 수 없다.
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
		// 결과 조회 API 가 이 값을 읽어 붐빔 정도를 화면에 내보낸다. 여기서 안 남기면 그 칸이
		// 영원히 빈다. 없으면 null 이고, 그 null 은 "안 붐빈다" 가 아니라 "모른다" 다.
		featureValues.put("CROWDING_SCORE", extractPlaceScore(candidate, "CROWDING_SCORE"));

		// ── 이동 경고 — FAIL 은 아니지만 감점한다 ────────────────────────────
		if (warnings.contains("STAIRS_PRESENT")) {
			total -= MOBILITY_WARNING_PENALTY;
		}
		if (warnings.contains("WALKING_OVER_LIMIT")) {
			total -= MOBILITY_WARNING_PENALTY;
		}
		// 접근성 미확인도 같은 폭으로 깎는다. 빼지는 않고 뒤로 민다.
		if (warnings.contains(ACCESSIBILITY_UNVERIFIED_WARNING)) {
			total -= MOBILITY_WARNING_PENALTY;
		}
		total = Math.max(0.0, total);

		// 미확인 제약을 제외할지는 여기서 정하지 않는다. 채점기는 사실만 보고하고 — 무엇이
		// 미확인이고 등급이 무엇인지를 unknownFacts 에 남긴다 — 제외 여부는 CandidateAssembler
		// 가 unknown-exclusion-threshold 설정과 견줘 정한다. 여기서 점수를 null 로 지우면 그
		// 설정이 동작하지 않으면서 동작하는 것처럼 보인다.
		Double preRankScore = Double.valueOf(total);

		return new EngineCandidate(candidate.placeId(), CANDIDATE_SOURCE, verdict, violations, unknownFacts,
				null, featureValues, scoreComponents, preRankScore, reasonCodes, warnings);
	}

	/**
	 * 제약 판정만 하고 점수는 매기지 않는다. Editor's Pick 기준선이 쓴다 — 순서를 사람이
	 * 정했으므로 필요한 것은 이 장소가 제약을 어기는가 하나다.
	 *
	 * <p>{@link #score} 를 부르지 않는 이유는 거리 성분 때문이다. Pick 에는 출발지 기준 거리가
	 * 없는데 {@code distanceM} 에 0 을 넣으면 "출발지에 붙어 있다" 가 되고, 그 값이
	 * {@code feature_values} 에 남아 거리 분포 질의를 오염시킨다.
	 *
	 * @param reasonCodes 이 후보에 붙일 이유 코드 (Pick 이면 {@code EDITORIAL_PICK})
	 * @param extraWarnings 부르는 쪽이 이미 아는 경고. 판정으로 나온 경고와 합쳐진다
	 * @return {@code preRankScore}·{@code featureValues}·{@code scoreComponents} 가 비어 있는
	 *     후보. 비어 있는 것이 사실이다 — 점수를 매기지 않았다
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
			// SELECTED 가 아니면(NONE=없다고 답함, UNKNOWN=안 물어봄) 대조할 값 자체가 없다.
			if (constraint.answerStatus() != TripConstraint.AnswerStatus.SELECTED) {
				continue;
			}
			String type = constraint.type() == null ? "" : constraint.type().toUpperCase(Locale.ROOT);
			switch (type) {
				case "ALLERGY" ->
						evaluateAllergy(candidate, constraint, constraintCodeMap, violations, unknownFacts, warnings);
				case "DIET" ->
						evaluateDiet(candidate, constraint, constraintCodeMap, violations, unknownFacts, warnings);
				case "MOBILITY" ->
						evaluateMobility(candidate, constraint, constraintCodeMap, violations, unknownFacts, warnings);
				default -> {
					// 알려지지 않은 제약 종류 — 이 엔진이 아는 셋(ALLERGY/DIET/MOBILITY) 밖이면
					// 판정할 근거가 없다. 조용히 건너뛴다(DB CHECK 가 애초에 이 셋만 허용한다).
				}
			}
		}
	}

	/**
	 * 알레르기.
	 *
	 * <p>🔴 <b>확인 안 된 곳은 빼지 않고 경고만</b> 단다 — S15P21E201-1633(사용자 결정 2026-09-25). 앱은 이제 알레르기를
	 * 묻지 않는데(-1497), 옛 「반드시」 답이 남은 여행은 후보 200곳이 전부 「확인 안 됨」이라 빠져 다시 짜기가 실패했다
	 * (9/21~22 실패 7건 중 4건). 알레르기 재료가 <b>들었다고 확인된</b> 곳은 지금처럼 뺀다 — 확인된 사실이다.
	 */
	private void evaluateAllergy(PlaceCandidateResponse.Candidate candidate, TripConstraint constraint,
			List<UserPlaceCodeMap> constraintCodeMap, List<Map<String, Object>> violations,
			List<Map<String, Object>> unknownFacts, List<String> warnings) {

		String featureType = hardFilterFeatureType(constraintCodeMap, "ALLERGY").orElse(null);
		if (featureType == null) {
			// 그냥 return 하면 안 된다. 대조표에 줄이 없는 것은 "판정할 필요가 없다" 가 아니라
			// "판정할 수 없다" 이고, 안전 제약에서 둘을 같게 다루면 땅콩이 들었는지 아무도
			// 모르는 식당이 통과한다. 모른다는 사실을 그대로 남긴다.
			unknownFacts.add(Map.of("fact", "ALLERGEN_MAPPING_MISSING", "featureKey",
					constraint.constraintKey(), "severity", severityOf(constraint)));
			return;
		}
		String code = constraint.constraintKey();
		switch (bucketFor(candidate, featureType, code)) {
			case PRESENT -> violations.add(Map.of("code", "ALLERGEN_PRESENT", "featureKey", code));
			case UNVERIFIED -> {
				if (!warnings.contains(ALLERGEN_UNVERIFIED_WARNING)) {
					warnings.add(ALLERGEN_UNVERIFIED_WARNING);
				}
			}
			case ABSENT -> {
				// 확인된 해당 없음 — 통과 기여. 추가로 할 일이 없다.
			}
		}
	}

	private void evaluateDiet(PlaceCandidateResponse.Candidate candidate, TripConstraint constraint,
			List<UserPlaceCodeMap> constraintCodeMap, List<Map<String, Object>> violations,
			List<Map<String, Object>> unknownFacts, List<String> warnings) {

		String featureType = hardFilterFeatureType(constraintCodeMap, "DIET").orElse(null);
		if (featureType == null) {
			// 알레르기와 같은 이유로 조용히 넘어가지 않는다.
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
				// 확인된 「지원 안 함」은 그대로 탈락이다. 모르는 것이 아니라 아는 것이라
				// 거르는 것이 맞다.
				case ABSENT -> violations.add(Map.of("code", "DIET_NOT_SUPPORTED", "featureKey", code));
				// 🔴 안 재 본 것은 «탈락이 아니라 경고» 다 — 접근성(evaluateMobility)과 같은
				//    자리, 같은 이유. unknownFacts 에 남기면 unknown-exclusion-threshold
				//    (기본 REQUIRED)가 그 후보를 빼는데, DIETARY_SUPPORT_TAG 가 운영에 0건이라
				//    식단을 고르기만 하면 후보가 전부 빠져 여행을 못 만들었다.
				//    팀 결정으로 식단은 「거르지 말고 확인 못 했다고 말한다」로 갔다.
				//    🔴 알레르기는 이 처리를 «일부러» 안 받는다 — 접근성·식단이 틀리면
				//    불편하지만 알레르기가 틀리면 사람이 다친다. evaluateAllergy 를 건드리지 말 것.
				case UNVERIFIED -> warnings.add(DIET_UNVERIFIED_WARNING);
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
	 * <p>접근성 표식은 셋을 가른다. PRESENT(재 봤고 갈 수 있다)는 통과, ABSENT(재 봤고 못
	 * 간다)는 탈락, UNVERIFIED(안 재 봤다)는 경고 + 감점이다. 안 재 봤다를 탈락과 같게 다루면
	 * 표식이 붙은 장소가 전체의 4% 뿐이라 휠체어 조건만 켜도 후보가 0건이 된다.
	 *
	 * <p>{@code unknown-exclusion-threshold} 를 {@code NONE} 으로 내려 푸는 방법은 쓰지 않는다 —
	 * 그 다이얼은 눈금이 하나뿐이라 알레르기 미확인까지 같이 풀린다.
	 *
	 * <p>감점된 후보에는 {@link #ACCESSIBILITY_UNVERIFIED_WARNING} 이 붙어 응답까지 간다.
	 * 「휠체어로 갈 수 있음」은 지킬 수 없는 약속이므로 화면은 잰 것을 그대로 말해야 한다.
	 */
	private void evaluateMobility(PlaceCandidateResponse.Candidate candidate, TripConstraint constraint,
			List<UserPlaceCodeMap> constraintCodeMap, List<Map<String, Object>> violations,
			List<Map<String, Object>> unknownFacts, List<String> warnings) {

		String key = constraint.constraintKey();

		if ("STAIRS_AVOIDANCE".equals(key)) {
			String featureType = flagCompareFeatureType(constraintCodeMap, "MOBILITY").orElse(null);
			if (featureType != null && bucketFor(candidate, featureType, null) == PresenceBucket.PRESENT) {
				// FAIL 이 아니다 — 경고 + 감점만 한다.
				warnings.add("STAIRS_PRESENT");
			}
			return;
		}
		if ("MAX_WALKING_METERS".equals(key)) {
			if (constraint.threshold() != null && candidate.distanceM() > constraint.threshold()) {
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
			// 방향이 알레르기와 반대다 — 여기는 "없다고 확인됨" 이 FAIL 이다.
			case ABSENT -> violations.add(Map.of("code", "ACCESS_VERIFIED_UNAVAILABLE", "featureKey", key));
			case UNVERIFIED -> {
				warnings.add(ACCESSIBILITY_UNVERIFIED_WARNING);
				evaluateSlope(candidate, constraint, violations, warnings);
			}
			case PRESENT -> {
				// 검증된 접근 가능 — 통과 기여.
			}
		}
	}

	/**
	 * 확인된 접근성 표식이 없는 곳을 경사로 가른다 — S15P21E201-1625.
	 *
	 * <p>🔴 왜. 접근성 표식은 운영 6,933곳 중 111줄뿐이라, 유아차를 「반드시」로 골라도 봉래산·사자봉 조망 지점
	 * 같은 산이 「미확인」 경고만 달고 일정에 들어갔다(여행 79da403f). 경사(주변 걷는 길의 가운데 값)는 거의 모든
	 * 장소에 있다.
	 *
	 * <p>규칙: 상한을 넘으면 「반드시」는 빼고 「되도록」은 경고만. 경사를 모르면 아무것도 더하지 않는다 — 위의 미확인
	 * 경고가 이미 붙어 있다(전과 같다). 상한 아래여도 미확인 경고는 그대로 둔다 — 경사는 둘레 길로 짐작한 추정값이지
	 * 그 장소를 잰 것이 아니라, 「갈 수 있음」을 약속하지 못한다.
	 *
	 * <p>확인된 표식이 있으면 여기까지 오지 않는다 — 확인된 사실이 추정값보다 앞선다.
	 * 계단은 경사 자료에 없다(계단은 따로 {@code STAIRS_AVOIDANCE} 가 본다).
	 */
	private void evaluateSlope(PlaceCandidateResponse.Candidate candidate, TripConstraint constraint,
			List<Map<String, Object>> violations, List<String> warnings) {
		Double slope = extractPlaceScore(candidate, "SLOPE_PERCENT");
		if (slope == null || slope <= this.maxSlopePercent) {
			return;
		}
		if (constraint.severity() == TripConstraint.Severity.HARD) {
			violations.add(Map.of("code", SLOPE_OVER_LIMIT, "featureKey", constraint.constraintKey(),
					"slopePercent", slope, "maxSlopePercent", this.maxSlopePercent));
		}
		else if (!warnings.contains(SLOPE_OVER_LIMIT)) {
			warnings.add(SLOPE_OVER_LIMIT);
		}
	}

	/** severity 를 정하는 유일한 통로. HARD → REQUIRED, SOFT → PREFERRED. */
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

	/**
	 * 이 장소의 음식 종류 표식 — 다양성 재정렬이 「같은 음식이 거듭되나」를 보는 축이다.
	 *
	 * <p>🔴 <b>점수에는 안 쓴다.</b> 음식 취향이 맞는 정도는
	 * {@code applyTagComponent(FOOD_PREFERENCE, weights.cuisine())} 이 이미 매기고, 여기서 또
	 * 더하면 같은 사실을 두 번 세는 것이 된다. 이 값은 <b>순서를 고르게 만드는 데만</b> 쓰인다 —
	 * {@code localityBucket} 을 남기는 이유와 같다.
	 *
	 * <p>표식 이름을 하드코딩하지 않고 대조표에서 {@code FOOD_PREFERENCE} 의 짝을 읽는다.
	 * 다른 태그 항들과 같은 규칙이다 — 대조표가 바뀌면 이 검색도 따라가야 한다.
	 */
	private List<String> cuisineTagsOf(PlaceCandidateResponse.Candidate candidate,
			List<UserPlaceCodeMap> preferenceCodeMap) {

		String featureType = featureTypeFor(preferenceCodeMap, "FOOD_PREFERENCE").orElse(null);
		if (featureType == null) {
			return List.of();
		}
		List<String> tags = new ArrayList<>();
		for (PlaceFeatureView feature : candidate.features()) {
			if (featureType.equals(feature.featureType()) && feature.featureKey() != null
					&& FeaturePresence.indicatesPresence(feature.evidenceStatus(), rawValue(feature))
					&& !tags.contains(feature.featureKey())) {
				tags.add(feature.featureKey());
			}
		}
		return tags;
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
	 * 접힌 취향 벡터가 {@code CATEGORY} 겹침에 더하는 덧점수. 기존 채점을 대체하지 않고 더하기만
	 * 한다 — 아직 벡터가 없는 사람이 대부분이라, 대체하면 그 사람들의 취향 반영이 0 이 된다.
	 *
	 * <p><b>행동이 들어간 성분만 더한다</b>({@code INTERACTION}·{@code BLENDED}). 설문만으로 접힌
	 * 성분은 {@link #applyTagComponent} 가 이미 같은 답으로 채점하므로, 여기서 또 더하면 두 번 세기가
	 * 된다. 이 거름은 그 일이 <b>생기지 않게 미리 걸어 둔 자물쇠</b>이지 지금 일어나는 일을 고친 것이
	 * 아니다 — 아래를 보라.
	 *
	 * <h2>이 항은 «설문만 있던 동안» 언제나 0 이었다</h2>
	 *
	 * 실서버 {@code user_taste_weight} 에 <b>{@code CATEGORY} 행이 0건</b>이었다 (2026-09-21 실측).
	 * 있는 것은 {@code FOOD_PREFERENCE} 21 · {@code LOCALITY} 12 · {@code QUIETNESS} 12 ·
	 * {@code TOURIST_PREFERENCE} 12 이고 전부 {@code SURVEY} 였다. 계정 설문이 {@code CATEGORY}
	 * 차원을 안 싣기 때문인데, 그것은 설문의 결함이 아니라 결정이다 —
	 * {@code PreferenceDefaultsService.CARRY_OVER} 가 <i>「CATEGORY·ATMOSPHERE 는 사람의 성향이
	 * 아니라 그 여행의 성격이라」</i> 일부러 뺀다.
	 *
	 * <h2>🔴 정정 (2026-09-22) — 여기 적혀 있던 「둘 중 하나를 정해야 한다」가 틀렸다</h2>
	 *
	 * 옛 주석은 {@code CATEGORY} 성분을 얻으려면 <i>「설문 문항을 건드려야 해서 범위가 크다」</i>
	 * 고 적었다. <b>설문을 건드릴 필요가 없었다.</b> 셋째 길이 있다 —
	 * {@link com.gabolle.backend.batch.application.BehaviorTasteFolder} 가 좋아요·제외 이벤트의
	 * 장소에서 {@code place_feature.CATEGORY_TAG} 를 읽고 대조표({@code user_place_code_map})를
	 * 지나 {@code CATEGORY} 성분을 만든다 (S15P21E201-1482). 설문은 그대로 두고 장소 쪽 표식에서
	 * 차원이 나온다.
	 *
	 * <p>그래서 아래 거름 두 줄은 이제 <b>실제로 걸리는 조건</b>이다. 설문 성분은 걸러지고
	 * ({@code applyTagComponent} 가 이미 채점했으므로) 행동 성분만 남는다 — 그것이 이 항의
	 * 존재 이유이고, 이제 그 성분이 실제로 존재한다.
	 *
	 * <p>다른 차원({@code FOOD_PREFERENCE}·{@code LOCALITY}·{@code QUIETNESS})은 <b>여전히 안
	 * 읽는다.</b> 그 셋은 설문 경로가 이미 채점하므로 여기서 또 더하면 <b>그때 비로소 진짜 두 번
	 * 세기</b>가 된다. 읽고 싶으면 그 경로와의 관계를 먼저 정해야 한다.
	 *
	 * <p>{@link #applyTagComponent} 가 맞은 개수 ÷ 고른 개수인 것과 달리 여기서는 맞은 성분의
	 * 가중치 합 ÷ 벡터의 CATEGORY 성분 개수를 쓴다. 전부 맞고 가중치가 1.0 이면 1.0 이라 같은
	 * 축이고, 가중치가 음수면(싫어하는 갈래) 총점이 내려간다 — 개수만 세면 못 하는 일이다.
	 *
	 * <p>온보딩 취향 여섯 중 장소에 실제로 붙는 것은 {@code FOOD}(모든 장소라 변별력이 없다)와
	 * {@code CAFE_HEALING} 둘뿐이다. 효과가 작아 보이면 배수가 아니라 그쪽을 먼저 본다.
	 *
	 * @param tasteWeights 이 사용자의 현재 판 성분 전부. 요청당 한 번 읽어서 넘어온다 —
	 *     후보마다 다시 읽으면 질의 개수가 후보 수에 비례한다
	 */
	private double applyTasteVectorComponent(PlaceCandidateResponse.Candidate candidate,
			List<UserPlaceCodeMap> preferenceCodeMap, List<TasteWeightComponent> tasteWeights,
			double multiplier, Map<String, Object> featureValues, Map<String, Object> scoreComponents,
			List<String> reasonCodes) {

		String featureType = featureTypeFor(preferenceCodeMap, "CATEGORY").orElse(null);
		// 🔴 설문만으로 접힌 성분은 뺀다. 그 답은 applyTagComponent 의 CATEGORY 태그 겹침이 이미
		//    채점했으므로, 여기서 또 더하면 같은 설문을 배수만큼 한 번 더 세는 것이 된다.
		//    남는 것은 행동이 들어간 성분(INTERACTION·BLENDED)뿐이고, 그것이 이 항의 존재 이유다.
		List<TasteWeightComponent> categoryWeights = (tasteWeights == null) ? List.of()
				: tasteWeights.stream()
						.filter((w) -> w.dimension() == TasteDimension.CATEGORY)
						.filter((w) -> w.evidence() != TasteEvidence.SURVEY)
						.toList();

		if (featureType == null || categoryWeights.isEmpty()) {
			// 값을 0.0 이 아니라 null 로 둔다 — "겹친 게 없다" 와 "잴 것이 없다" 는 다르다.
			// 행동이 아직 하나도 안 접힌 동안에는 언제나 이 자리다.
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
		for (TasteWeightComponent weight : categoryWeights) {
			if (placeTags.contains(weight.code())) {
				matched.put(weight.code(), weight.weight());
				sum += weight.weight();
			}
		}
		double ratio = sum / categoryWeights.size();

		featureValues.put("tasteVectorOverlap", ratio);
		// evidence 를 함께 남긴다. 무엇을 근거로 더했는지가 행에 있어야 「이 점수가 행동에서
		// 왔는가 설문에서 왔는가」를 나중에 되짚을 수 있다.
		scoreComponents.put("tasteVectorContribution", componentDetail(multiplier, ratio,
				Map.of("matched", matched, "componentCount", categoryWeights.size(),
						"evidence", evidenceSummary(categoryWeights))));
		if (!matched.isEmpty()) {
			reasonCodes.add("TASTE_VECTOR_MATCH");
		}
		return multiplier * ratio;
	}

	/** 성분들이 무엇을 근거로 접혔는지 — {@code SURVEY} · {@code INTERACTION} · {@code BLENDED} 별 개수. */
	private static Map<String, Integer> evidenceSummary(List<TasteWeightComponent> weights) {
		Map<String, Integer> counts = new LinkedHashMap<>();
		for (TasteWeightComponent weight : weights) {
			counts.merge(weight.evidence().name(), 1, Integer::sum);
		}
		return counts;
	}

	private double applyTagComponent(PlaceCandidateResponse.Candidate candidate, PreferenceSnapshot preferenceSnapshot,
			List<UserPlaceCodeMap> preferenceCodeMap, String preferenceCode,
			double weight, String componentKey, String reasonCode, String featureValueKey,
			Map<String, Object> featureValues, Map<String, Object> scoreComponents, List<String> reasonCodes) {

		// 검색할 장소 표식 유형도 대조표에서 읽은 featureType 을 그대로 쓴다 — 문자열을
		// 하드코딩하면 대조표가 바뀌어도 이 검색은 안 따라간다.
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

		// 🔴 여행 테마(CATEGORY)는 장소의 갈래(place.category)와도 맞춰 본다 (2026-09-23, S15P21E201-1535).
		//    앱의 테마 코드(SEA_BEACH·CITY·CAFE_HEALING·CULTURE_TEMPLE·FOOD·NATURE_WALK)는 place.category 와
		//    같은 어휘인데, 관심 태그(INTEREST_TAG)는 다른 어휘다(운영: NATURE·WALK·TRADITIONAL_MARKET …).
		//    태그만 보면 테마가 어떤 장소와도 안 겹쳐 가산이 0 이었다. 전에는 번역기가 갈래로 후보를
		//    «잘라서» 테마를 반영했는데 그 거르기를 없앴으므로(BaselineCandidateTranslator) 여기서 가산한다.
		//    장소는 갈래가 하나라 비율이 아니라 «맞으면 만점» 이다 — 테마 둘을 고른 사람에게 바다가 반점이면 안 된다.
		if ("CATEGORY".equals(preferenceCode) && candidate.category() != null
				&& userCodes.contains(candidate.category())) {
			if (!matched.contains(candidate.category())) {
				matched.add(candidate.category());
			}
			ratio = 1.0;
		}

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
