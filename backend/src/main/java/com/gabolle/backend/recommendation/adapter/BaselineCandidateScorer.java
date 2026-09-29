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
import java.util.regex.Pattern;

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

import tools.jackson.databind.JsonNode;
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
	 * 재 봤더니 이동 조건(휠체어·유아차 등)으로 갈 수 없다고 나온 곳. 「반드시」면 탈락 사유 코드, 「되도록」이면
	 * 경고 코드로 같은 낱말을 쓴다 — {@link #SLOPE_OVER_LIMIT} 과 같은 방식이다.
	 */
	static final String ACCESS_VERIFIED_UNAVAILABLE = "ACCESS_VERIFIED_UNAVAILABLE";

	/**
	 * 취향 한 축의 정렬도(1 - |장소 값 - 취향 값|, 0~1)가 이 값 이상일 때만 「취향에 맞음」 이유 코드
	 * ({@code PREF_ALIGNED_*})를 단다. 0.5 는 눈금의 한가운데다 — 이보다 낮으면 장소가 취향과 반대쪽 절반에
	 * 있다는 뜻이라 「맞아서 골랐다」고 말할 수 없다. 점수 계산에는 쓰지 않고 이유 코드에만 쓴다.
	 */
	static final double PREF_ALIGNED_MIN_ALIGNMENT = 0.5;

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
		List<String> imputedDimensions = new ArrayList<>();
		applyAlignmentDimension(candidate, preferenceSnapshot, preferenceCodeMap, "LOCALITY", "localityScore",
				false, featureValues, alignments, imputedDimensions, reasonCodes);
		applyAlignmentDimension(candidate, preferenceSnapshot, preferenceCodeMap, "QUIETNESS", "quietnessScore",
				false, featureValues, alignments, imputedDimensions, reasonCodes);
		applyAlignmentDimension(candidate, preferenceSnapshot, preferenceCodeMap, "TOURIST_PREFERENCE",
				"touristRatio", false, featureValues, alignments, imputedDimensions, reasonCodes);
		applyAlignmentDimension(candidate, preferenceSnapshot, preferenceCodeMap, "SHADE_PREFERENCE", "shadeScore",
				false, featureValues, alignments, imputedDimensions, reasonCodes);
		// SLOPE_PERCENT 는 0~100 퍼센트이고 선호값은 0~1 스케일이다 — 비교 전에 100 으로 나눠
		// 같은 축으로 맞춘다.
		applyAlignmentDimension(candidate, preferenceSnapshot, preferenceCodeMap, "SLOPE_PREFERENCE", "slopePercent",
				true, featureValues, alignments, imputedDimensions, reasonCodes);

		Double alignmentAverage = alignmentWeights.weightedAverage(alignments);
		// dimensions 옆에 dimensionWeights 를 같이 남긴다. 정렬도만 남기면 "이 장소가 왜 이
		// 순위인가" 를 되짚을 때 어느 축이 얼마나 셌는지를 알 수 없다. imputedDimensions 는
		// 장소 값이 없어 중간값으로 채운 축이다 — dimensions 의 그 숫자는 잰 것이 아니다.
		scoreComponents.put("preferenceAlignment", componentDetail(weights.preferenceAlignment(), alignmentAverage,
				Map.of("dimensions", alignments, "dimensionWeights", alignmentWeights.weightsUsed(alignments),
						"imputedDimensions", imputedDimensions)));
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
		// 「되도록」인데 재 보니 못 간다고 나온 곳 — 빼지는 않지만 안 재 본 곳보다 나을 리 없으니 같은 폭으로 민다.
		if (warnings.contains(ACCESS_VERIFIED_UNAVAILABLE)) {
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

		// 🔴 채식·비건은 고기가 중심인 집을 «확인된 사실»로 뺀다 — S15P21E201-1815.
		//    식단 지원 표식(DIETARY_SUPPORT_TAG)이 운영에 0건이라 아래 판정은 전부 「확인 안 됨」
		//    경고로 끝나고, 채식을 골라도 갈비집이 일정에 들어갔다(QA). 상호명·음식 태그가 고기를
		//    가리키면 그 집이 채식을 지원하지 않는다는 것은 추정이 아니라 사실에 가깝다.
		//    뺄 곳이 많아져도 고기집으로 채우지 않는다 — 식사 자리가 줄어드는 쪽이 낫다.
		//    🔴 S15P21E201-1822: 이름에 「고기」가 없는 고깃집(감자탕·국밥·면옥·까르니따스)이 비건 후보에
		//    남았다. 채식은 고기 육수 집까지, 비건은 회·초밥·해물·멸치 육수 집까지 뺀다.
		//    S15P21E201-1828: 식단을 채식 하나로 합쳤다. 달걀·유제품은 우리 자료(이름·대표 메뉴·방문 이유)로
		//    가릴 수 없어 비건과 채식의 차이를 지킬 수 없고, 페스코는 거르는 규칙이 없어 갈비집이 통과했다.
		//    그래서 채식·비건·페스코 모두 고기·해산물 중심 집을 뺀다. 앱은 채식만 고르게 하고, 옛 여행에 남은
		//    VEGAN·PESCATARIAN 은 채식과 똑같이 판정한다.
		//    🔴 S15P21E201-1828: 이름만 보면 대패·식육·라멘·짬뽕 집이 남고, 이름이 고기를 말하지 않는 집(돈반 — 경양식
		//    돈까스, 신흥관 — 사천짜장)은 아예 못 잡았다. 적재된 대표 메뉴(MENU_PRICE_WON)와 방문 이유
		//    (WHY_VISIT) 글도 같은 낱말로 본다. 운영 식당 4,298곳 중 이름 말고 글이 있는 곳이 1,550곳이고,
		//    실제로 추천된 식당 341곳 중에서는 262곳이다(2026-09-29 실측).
		String dietKey = upper(constraint.constraintKey());
		// 🔴 S15P21E201-1829: 할랄은 술이 중심인 집(주점·이자카야·포차·펍·맥주집)을 무엇을 팔든 뺀다. 채식 집이어도 술집이면
		//    뺀다 — 그래서 아래 식물성 이름 검사보다 먼저 본다.
		if ("HALAL".equals(dietKey) && isAlcoholCentric(candidate)) {
			violations.add(Map.of("code", "DIET_NOT_SUPPORTED", "featureKey", constraint.constraintKey(),
					"reason", "ALCOHOL_CENTRIC", "evidence", "NAME"));
			return;
		}
		if (MEAT_EXCLUDING_DIETS.contains(dietKey) && !isExplicitlyPlantBased(candidate)) {
			DietEvidence evidence = dietExclusionEvidence(candidate, SEAFOOD_EXCLUDING_DIETS.contains(dietKey));
			if (evidence != null) {
				violations.add(Map.of("code", "DIET_NOT_SUPPORTED", "featureKey", constraint.constraintKey(),
						"reason", evidence.reason(), "evidence", evidence.source()));
				return;
			}
		}

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
	 * 고기가 중심인 집을 빼는 식단 코드.
	 *
	 * <p>🔴 할랄(S15P21E201-1829)은 고기의 «종류»를 가리지 않는다. 할랄 도축을 확인할 수 없는 고기는 소·닭·꿩이어도 할랄이
	 * 아니므로, 「국밥」이 돼지인지 소인지 몰라도 된다(해운대원조할매국밥은 소고기국밥이다 — 이름만 보고
	 * 「돼지」라고 말하면 틀린다). 그래서 빠지는 이유도 MEAT_CENTRIC 이지 돼지가 아니다.
	 * 해산물은 할랄에서 뺄 근거가 아니다 — 한국관광공사 무슬림 친화 식당 목록(2021-12 기준)의 부산
	 * 한식당이 복국·대구탕·아구찜 집이다. 할랄이라고 확인된 곳(DIETARY_SUPPORT_TAG)은 아직 0건이라
	 * 남는 곳은 전부 「확인 안 됨」 경고로 통과한다.
	 */
	private static final Set<String> MEAT_EXCLUDING_DIETS = Set.of("VEGETARIAN", "VEGAN", "PESCATARIAN", "HALAL");

	/** 해산물 중심 집까지 빼는 식단 — 채식 하나로 합친 코드들(S15P21E201-1828). 할랄은 해산물을 빼지 않는다. */
	private static final Set<String> SEAFOOD_EXCLUDING_DIETS = Set.of("VEGETARIAN", "VEGAN", "PESCATARIAN");

	/**
	 * 할랄 — 술이 중심인 집의 이름 낱말. 운영 식당·카페·도시 장소 이름 전부에 대 보고 골랐다(2026-09-29).
	 * 이 낱말들은 술집에만 걸렸다. 「사케」는 뺐다 — 사케동(연어 덮밥)에 걸린다.
	 *
	 * <p>이름으로만 본다. 대표 메뉴·방문 이유 글의 「와인 페어링」「하이볼」은 술을 «파는» 집이지 술이
	 * «중심인» 집이 아니고, 한국관광공사 기준으로도 무슬림 친화 식당은 주류를 팔 수 있다.
	 */
	private static final List<String> ALCOHOL_NAME_WORDS = List.of(
			"주점", "술집", "혼술", "이자카야", "포차", "포장마차", "호프", "펍", "와인", "칵테일", "하이볼", "막걸리",
			"맥주", "비어", "소주", "위스키");

	/**
	 * 영어 이름의 술집 낱말 — 앞뒤가 영문자·숫자가 아닐 때만 본다. 「PUB」은 PUBLIC 에, 「BAR」는
	 * BARBECUE·BARN 에 걸리면 안 된다. 🔴 {@code \b} 를 쓰지 않는다 — JDK 판에 따라 한글을 낱말 글자로
	 * 쳐서 「로즈bar」의 즈와 B 사이를 경계로 보지 않는다(시험이 잡았다).
	 */
	private static final Pattern ALCOHOL_NAME_LATIN = Pattern.compile(
			"(?<![A-Z0-9])(PUB|BAR|BEER|WINE|COCKTAIL|IZAKAYA|HOF|WHISKY|WHISKEY|BREWERY|HIGHBALL)(?![A-Z0-9])");

	/** 「바」가 술집이 아닌 영어 이름 — 먼저 지운다. */
	private static final List<String> NOT_A_DRINKING_BAR = List.of(
			"SNACK BAR", "SALAD BAR", "JUICE BAR", "ESPRESSO BAR", "DESSERT BAR");

	/**
	 * 식당(FOOD) 이름에서만 보는 한 글자 「술」. 운영 식당 중 11곳에 걸렸고 전부 술집이었다(혼술바·술잔·
	 * 청하통술…). 미술관·예술 같은 낱말은 먼저 지우고, 식당이 아닌 갈래에서는 아예 안 본다.
	 */
	private static final List<String> SUL_LOOKALIKES = List.of("예술", "미술", "기술", "마술", "수술");

	static boolean isAlcoholCentric(PlaceCandidateResponse.Candidate candidate) {
		String name = cleaned(candidate.nameKo());
		if (containsAny(name, ALCOHOL_NAME_WORDS)) {
			return true;
		}
		String latin = name;
		for (String phrase : NOT_A_DRINKING_BAR) {
			latin = latin.replace(phrase, "");
		}
		if (ALCOHOL_NAME_LATIN.matcher(latin).find()) {
			return true;
		}
		if (!"FOOD".equals(candidate.category())) {
			return false;
		}
		String withoutLookalikes = name;
		for (String word : SUL_LOOKALIKES) {
			withoutLookalikes = withoutLookalikes.replace(word, "");
		}
		return withoutLookalikes.contains("술");
	}

	/**
	 * 상호명에 이 낱말이 있으면 고기가 중심인 집으로 본다. 「오리」는 뺐다(오리지널·오리엔탈).
	 */
	private static final List<String> MEAT_NAME_WORDS = List.of(
			"고기", "갈비", "삼겹", "목살", "돼지", "소고기", "한우", "곱창", "막창", "대창", "양곱창",
			"족발", "보쌈", "치킨", "통닭", "닭갈비", "닭강정", "양꼬치", "불고기", "육회", "정육",
			"스테이크", "바베큐", "바비큐", "BBQ", "숯불", "순대", "수육", "돈까스", "돈가스", "삼계탕",
			// S15P21E201-1822 — 이름에 고기가 안 보여도 고기 육수·고기가 주인 집. 국밥은 콩나물국밥도
			// 멸치·고기 육수를 쓰므로 통째로 뺀다. 밀면·냉면·면옥은 소·돼지 육수다.
			"감자탕", "뼈해장", "해장국", "국밥", "곰탕", "설렁탕", "육개장", "곱도리", "닭한마리", "찜닭",
			"닭발", "까르니따스", "카르니타스", "CARNITAS", "케밥", "KEBAB", "밀면", "냉면", "면옥",
			// 애매하지만 뺀다 — 채식 메뉴가 있을 수는 있어도 고를 근거가 없다. 뷔페는 고기가 반드시 있다.
			"버거", "BURGER", "샤브", "뷔페", "BUFFET",
			// 운영 비건·채식 후보로 실제 새던 이름에서 모았다(2026-09-29). 부위·조리법(대패·항정·가브리살·
			// 식육·제육), 고기 육수가 기본인 면(라멘·쌀국수·돈코츠·차슈), 돼지고기가 든 중식(짜장·짬뽕),
			// 이름 끝이 고기인 외국 음식(카츠·타코·부리또·야키토리·부어스트·굴라쉬). 「오리」는 여전히
			// 홀로 쓰지 않는다 — 오리 요리 이름으로만 쓴다.
			"대패", "식육", "갈매기살", "항정", "가브리살", "꼬리곰탕", "소머리", "선지", "제육", "김치찜",
			"카츠", "가츠", "라멘", "짬뽕", "짜장", "쌀국수", "분짜", "반미", "야키토리", "꼬치", "로바타",
			"오리구이", "오리고기", "훈제오리", "오리백숙", "오리불고기", "오리주물럭", "오리탕", "후라이드",
			"순살", "육전", "육쌈", "핫도그", "소시지", "베이컨", "하몽", "차슈", "돈코츠", "타코", "TACO",
			"부리또", "BURRITO", "부어스트", "굴라쉬", "낙곱새", "PORK", "BEEF", "CHICKEN", "STEAK", "RAMEN",
			"KATSU");

	/**
	 * 식당(FOOD) 이름에서만 보는 한 글자 — 「돈」(돼지)·「닭」.
	 *
	 * <p>한 글자는 오탐이 무섭지만 식당 이름에서는 뜻이 하나다. 운영 식당 중 이름에 「돈」이 든 89곳을 훑었고
	 * (우돈애·배돈·뚱돈·송정돈가·돈반…) 돼지고기 집이 아닌 곳을 못 찾았다. 다른 갈래(문화·자연)에는
	 * 「돈대」 같은 이름이 있을 수 있어 식당에만 쓴다.
	 */
	private static final List<String> MEAT_SYLLABLES_FOOD_ONLY = List.of("돈", "닭");

	/** 음식 태그(CUISINE_TAG) 중 고기가 중심인 것. 돼지국밥·밀면(돼지·소 육수)이다. */
	private static final Set<String> MEAT_CUISINE_TAGS = Set.of("PORK_SOUP", "MILMYEON");

	/**
	 * 이 낱말이 이름에 있으면 무엇이 더 들어 있든 빼지 않는다 — 「채식 뷔페」·「비건 버거」는 그 식단을
	 * 위한 집이다.
	 */
	private static final List<String> PLANT_BASED_NAME_WORDS = List.of(
			"비건", "채식", "VEGAN", "VEGETARIAN", "사찰음식", "베지");

	/**
	 * 채식이 고기에 더해 빼는 해산물·생선 중심 낱말(S15P21E201-1822, 채식 통합은 S15P21E201-1828).
	 *
	 * <p>🔴 짧은 글자는 일부러 안 넣었다: 「회」(회관·회사·회현), 「게」(가게·게스트하우스),
	 * 「굴」(굴다리), 「복」(행복). 대신 「횟집」「회센터」「물회」「게장」「대게」처럼 뜻이 하나인
	 * 낱말만 쓴다. 그래서 「OO회관」은 이 목록으로는 안 빠진다(고깃집이면 위 고기 낱말이 잡는다).
	 * 카페·젤라또·빵집은 빼지 않는다 — 음료·빵은 비건일 수 있고, 유제품 여부는 이름으로 모른다.
	 * 칼국수는 뺀다 — 부산 칼국수 육수는 거의 멸치·해물이다.
	 */
	private static final List<String> SEAFOOD_NAME_WORDS = List.of(
			"횟집", "회센터", "물회", "생선회", "활어", "수산", "스시", "초밥", "마끼", "사시미", "연어", "살몬",
			"SALMON", "SUSHI", "참치", "장어", "대구탕", "복국", "복어", "아구", "아귀", "해물", "해산물",
			"씨푸드", "SEAFOOD", "조개", "전복", "게장", "대게", "홍게", "킹크랩", "새우", "오뎅", "어묵",
			"멸치", "칼국수", "낙지", "문어", "주꾸미", "쭈꾸미", "오징어", "생선", "고등어", "갈치",
			// 운영 비건 후보로 새던 이름에서 모았다(2026-09-29). 우동·소바는 가다랑어 육수가 기본이다.
			// 「소바」는 「에스프레소바」에 걸리므로 비교 전에 「에스프레소」를 지운다(cleaned).
			// 「도미」는 뺐다 — 도미노피자에 걸린다.
			"추어", "곰장어", "꼼장어", "석화", "생굴", "가리비", "멍게", "해삼", "성게", "오마카세", "자연산",
			"우동", "소바", "조개구이", "매운탕", "알탕", "꽃게", "게내장", "아나고", "붕장어", "광어", "재첩",
			"FISH", "OYSTER", "SHRIMP", "CRAB");

	/** 채식이 해산물 집으로 빼는 음식 태그 — CUISINE_TAG 의 해산물, DESIRED_FOOD_TAG 의 복국. */
	private static final Set<String> SEAFOOD_TAGS = Set.of("CUISINE_TAG:SEAFOOD", "DESIRED_FOOD_TAG:BOKGUK");

	/**
	 * 인도·네팔 식당 — 대표 메뉴가 닭고기 커리여도 채식 메뉴를 거의 늘 따로 둔다. 그래서 «글에 고기가 보인다»
	 * 로는 빼지 않는다(이름 근거는 그대로 본다). 확인된 것은 아니므로 예전처럼 「확인 안 됨」 경고로 남는다.
	 * 부산에서 채식을 고른 사람에게 남는 몇 안 되는 선택지라 글 근거로 지우면 잃는 것이 크다.
	 */
	private static final List<String> USUALLY_HAS_VEGETARIAN_DISHES = List.of("인도", "인디아", "INDIA", "네팔", "NEPAL");

	/** 대표 메뉴 — {@code {"priceWon":…,"menu":"…"}} 의 menu. */
	private static final String MENU_FEATURE = "MENU_PRICE_WON";

	/** 방문 이유 — {@code {"reasons":[{"type":…,"note":"…"}],"sources":[…]}} 의 note. 출처 주소는 안 본다. */
	private static final String WHY_VISIT_FEATURE = "WHY_VISIT";

	/**
	 * 고기·해산물 중심이라는 근거 하나와 그것을 어디서 봤는가. {@code source} 는 NAME_OR_TAG ·
	 * MENU_PRICE_WON · WHY_VISIT 중 하나다 — 운영에서 어떤 집이 왜 빠졌는지를 violations 에서 바로 되짚는다.
	 */
	record DietEvidence(String reason, String source) {
	}

	/**
	 * 채식·비건이 그 집을 빼야 할 근거. 없으면 null — 그때는 아래 식단 지원 표식 판정(대부분 「확인 안 됨」)으로 간다.
	 *
	 * <p>🔴 글 근거는 «빼는 쪽»으로만 쓴다. 글은 조사 에이전트가 모은 추정이라, 글에 「비건」이 있다고
	 * 통과시키지는 않는다(안전 판정을 추정으로 만들지 않는다 — S15P21E201-666 과 같은 원칙). 대신 어느 글에든
	 * 채식·비건 낱말이 있으면 그 집은 글 근거로 빼지 않는다 — 「비건 샌드위치도 있다」는 집을 메뉴의 햄으로
	 * 지우지 않기 위해서다.
	 */
	static DietEvidence dietExclusionEvidence(PlaceCandidateResponse.Candidate candidate, boolean excludeSeafood) {
		if (isMeatCentric(candidate)) {
			return new DietEvidence("MEAT_CENTRIC", "NAME_OR_TAG");
		}
		if (excludeSeafood && isSeafoodCentric(candidate)) {
			return new DietEvidence("SEAFOOD_CENTRIC", "NAME_OR_TAG");
		}
		if (containsAny(cleaned(candidate.nameKo()), USUALLY_HAS_VEGETARIAN_DISHES)) {
			return null;
		}
		Map<String, String> texts = describedTexts(candidate);
		if (texts.values().stream().anyMatch(text -> containsAny(cleaned(text), PLANT_BASED_NAME_WORDS))) {
			return null;
		}
		for (Map.Entry<String, String> text : texts.entrySet()) {
			// 글에서는 「자갈치」가 대개 위치다(「자갈치시장 앞 빵집」) — 「갈치」로 읽지 않는다. 이름에서는 지우지
			// 않는다: 이름의 자갈치는 거의 해산물 집이다(자갈치회센타·자갈치왕곰장어).
			String body = cleaned(text.getValue()).replace("자갈치", "");
			if (containsAny(body, MEAT_NAME_WORDS)) {
				return new DietEvidence("MEAT_CENTRIC", text.getKey());
			}
			if (excludeSeafood && containsAny(body, SEAFOOD_NAME_WORDS)) {
				return new DietEvidence("SEAFOOD_CENTRIC", text.getKey());
			}
		}
		return null;
	}

	/** 대표 메뉴와 방문 이유의 글만 모은다(순서 유지). 값이 없거나 모양이 다르면 빈 글로 본다. */
	private static Map<String, String> describedTexts(PlaceCandidateResponse.Candidate candidate) {
		Map<String, String> texts = new LinkedHashMap<>();
		if (candidate.features() == null) {
			return texts;
		}
		for (PlaceFeatureView feature : candidate.features()) {
			if (feature.value() == null) {
				continue;
			}
			if (MENU_FEATURE.equals(feature.featureType())) {
				texts.merge(MENU_FEATURE, feature.value().path("menu").asString(""), (a, b) -> a + " " + b);
			}
			else if (WHY_VISIT_FEATURE.equals(feature.featureType())) {
				StringBuilder notes = new StringBuilder();
				for (JsonNode reason : feature.value().path("reasons")) {
					notes.append(reason.path("note").asString("")).append(' ');
				}
				texts.merge(WHY_VISIT_FEATURE, notes.toString(), (a, b) -> a + " " + b);
			}
		}
		return texts;
	}

	/** 대문자로 맞추고, 고기·소바로 잘못 읽히는 낱말(물고기 · 에스프레소)을 지운다. */
	private static String cleaned(String value) {
		return value == null ? "" : value.toUpperCase(Locale.ROOT).replace("물고기", "").replace("에스프레소", "");
	}

	private static boolean containsAny(String text, List<String> words) {
		for (String word : words) {
			if (text.contains(word)) {
				return true;
			}
		}
		return false;
	}

	static boolean isExplicitlyPlantBased(PlaceCandidateResponse.Candidate candidate) {
		return containsAny(cleaned(candidate.nameKo()), PLANT_BASED_NAME_WORDS);
	}

	static boolean isSeafoodCentric(PlaceCandidateResponse.Candidate candidate) {
		if (containsAny(cleaned(candidate.nameKo()), SEAFOOD_NAME_WORDS)) {
			return true;
		}
		if (candidate.features() != null) {
			for (PlaceFeatureView feature : candidate.features()) {
				if (SEAFOOD_TAGS.contains(feature.featureType() + ":" + feature.featureKey())) {
					return true;
				}
			}
		}
		return false;
	}

	static boolean isMeatCentric(PlaceCandidateResponse.Candidate candidate) {
		// 「물고기」(수족관·체험)는 고깃집이 아니다 — cleaned 가 지운다.
		String name = cleaned(candidate.nameKo());
		if (containsAny(name, MEAT_NAME_WORDS)) {
			return true;
		}
		if ("FOOD".equals(candidate.category()) && containsAny(name, MEAT_SYLLABLES_FOOD_ONLY)) {
			return true;
		}
		if (candidate.features() != null) {
			for (PlaceFeatureView feature : candidate.features()) {
				if ("CUISINE_TAG".equals(feature.featureType()) && feature.featureKey() != null
						&& MEAT_CUISINE_TAGS.contains(feature.featureKey())) {
					return true;
				}
			}
		}
		return false;
	}

	private static String upper(String value) {
		return value == null ? "" : value.toUpperCase(Locale.ROOT);
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
			// 🔴 단 「반드시」일 때만이다. 「되도록」(SOFT)은 경사가 상한을 넘을 때(evaluateSlope)와 같이 빼지 않고
			//    경고로 단다. 예전에는 등급을 안 보고 늘 FAIL 로 적어서 「되도록」이 「반드시」처럼 굴었다.
			case ABSENT -> {
				if (constraint.severity() == TripConstraint.Severity.HARD) {
					violations.add(Map.of("code", ACCESS_VERIFIED_UNAVAILABLE, "featureKey", key));
				}
				else if (!warnings.contains(ACCESS_VERIFIED_UNAVAILABLE)) {
					warnings.add(ACCESS_VERIFIED_UNAVAILABLE);
				}
			}
			case UNVERIFIED -> {
				// 휠체어·유아차를 같이 고르면 이 갈래를 두 번 지난다. 경고는 「이 장소는 안 재 봤다」 한 가지 사실이라
				// 한 번만 단다 — 두 번 달면 화면에 같은 경고가 두 줄 뜬다.
				if (!warnings.contains(ACCESSIBILITY_UNVERIFIED_WARNING)) {
					warnings.add(ACCESSIBILITY_UNVERIFIED_WARNING);
				}
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
			Map<String, Double> alignments, List<String> imputedDimensions, List<String> reasonCodes) {

		String featureType = featureTypeFor(preferenceCodeMap, preferenceCode).orElse(null);
		Double placeScore = (featureType == null) ? null : extractPlaceScore(candidate, featureType);
		// 채운 값이 아니라 잰 값을 남긴다 — 설명 쪽은 null 을 「모름」으로 읽어야 한다.
		featureValues.put(featureValueKey, placeScore);

		Double prefScore = PreferenceJson.scoreFor(preferenceSnapshot, preferenceCode, this.objectMapper);
		if (prefScore == null) {
			// 사용자가 이 축을 안 봤다(「상관없어요」·무응답). 전처럼 축을 뺀다.
			return;
		}
		if (placeScore == null) {
			if (featureType == null) {
				// 대조표에 이 축이 없다 — 장소 탓이 아니라 모든 장소가 똑같이 모르는 것이라 뺀다.
				return;
			}
			// 🔴 모름이 평균보다 이기면 안 된다. 예전에는 장소 값이 없으면 이 축을 빼고 나머지로 평균을
			//    다시 냈다. 그러면 그늘 자료가 「없는」 곳이 그늘이 「중간인」 곳을 이겼다 — 그늘 점수는
			//    0~1 백분위라 중간인 곳은 「그늘 우선」(1.0)과 0.5 로 맞는데, 자료 없는 곳은 그 0.5 가 빠져
			//    경사 축 하나(0.97)만으로 평균이 났다. 자료를 모으지 않은 곳이 상을 받는 셈이다.
			//    그래서 빼지 않고 「장소 값이 0~1 에 고르게 퍼져 있다면 기대되는 정렬도」로 채운다:
			//    ∫₀¹ (1 - |x - p|) dx = 1 - (p² + (1-p)²) / 2. p=1 이면 0.5, p=0.5 면 0.75 다.
			//    맞춘 것이 없으니 PREF_ALIGNED_ 이유 코드는 붙이지 않는다.
			double p = clamp01(prefScore);
			alignments.put(preferenceCode, 1.0 - (p * p + (1.0 - p) * (1.0 - p)) / 2.0);
			imputedDimensions.add(preferenceCode);
			return;
		}
		double normalizedPlace = placeValueIsPercent ? placeScore / 100.0 : placeScore;
		double alignment = clamp01(1.0 - Math.abs(normalizedPlace - prefScore));
		alignments.put(preferenceCode, alignment);
		// 이유 코드는 「취향에 맞아서 골랐다」는 말이다. 예전에는 값을 쟀기만 하면 붙어서, 그늘 우선인데
		// 뙤약볕(정렬도 0.1)인 곳에도 「그늘 취향에 맞음」이 붙었다.
		if (alignment >= PREF_ALIGNED_MIN_ALIGNMENT) {
			reasonCodes.add("PREF_ALIGNED_" + preferenceCode);
		}
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
