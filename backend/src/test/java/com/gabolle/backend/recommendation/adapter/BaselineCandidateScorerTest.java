package com.gabolle.backend.recommendation.adapter;

import static org.assertj.core.api.Assertions.within;
import com.gabolle.backend.preference.domain.UserTasteWeight;
import com.gabolle.backend.preference.domain.TasteDimension;
import com.gabolle.backend.preference.domain.TasteWeightComponent;
import java.time.OffsetDateTime;
import java.time.Instant;
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
import com.gabolle.backend.trip.domain.PreferenceSnapshot;
import com.gabolle.backend.trip.domain.TripConstraint;

import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link UserPlaceCodeMap} 은 읽기 전용 JPA 엔티티라 public 생성자가 없다. Mockito 대역으로
 * DB 없이 대조표 행을 흉내 낸다.
 */
class BaselineCandidateScorerTest {

	private static final int RADIUS_M = 5000;

	/** 취향 벡터 배수. 기본값과 같은 값을 쓴다 — 검사가 설정과 따로 놀지 않게. */
	private static final double TASTE_MULTIPLIER = 0.05;

	private static final UUID TASTE_VECTOR_ID = UUID.randomUUID();

	/** 고정 시각. 성분의 updatedAt 은 이 검사들의 판정에 안 쓰이지만 지어내지는 않는다. */
	private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-09-16T12:00:00Z");

	private static final BaselineEngineProperties.Weights WEIGHTS =
			new BaselineEngineProperties.Weights(0.30, 0.20, 0.15, 0.15, 0.10, 0.10);

	/**
	 * 취향 다섯 차원의 비율 — 전부 기본값(1.0)이다. 비율이 같으면 가중평균이 단순평균과
	 * 같아서 아래 기대값들이 단순평균으로 계산된다.
	 */
	private static final PreferenceAlignmentWeights ALIGNMENT_WEIGHTS =
			new PreferenceAlignmentWeights(null, null, null, null, null);

	private final BaselineCandidateScorer scorer = new BaselineCandidateScorer(new ObjectMapper());

	private final List<UserPlaceCodeMap> preferenceCodeMap = List.of(
			codeMap("CATEGORY", "INTEREST_TAG", MatchKind.TAG_OVERLAP),
			codeMap("ATMOSPHERE", "ATMOSPHERE_TAG", MatchKind.TAG_OVERLAP),
			codeMap("FOOD_PREFERENCE", "CUISINE_TAG", MatchKind.TAG_OVERLAP),
			codeMap("LOCALITY", "LOCALITY_SCORE", MatchKind.SCORE_COMPARE),
			codeMap("QUIETNESS", "QUIETNESS_SCORE", MatchKind.SCORE_COMPARE),
			codeMap("TOURIST_PREFERENCE", "TOURIST_RATIO", MatchKind.SCORE_COMPARE),
			codeMap("SHADE_PREFERENCE", "SHADE_SCORE", MatchKind.SCORE_COMPARE),
			codeMap("SLOPE_PREFERENCE", "SLOPE_PERCENT", MatchKind.SCORE_COMPARE));

	private final List<UserPlaceCodeMap> constraintCodeMap = List.of(
			codeMap("ALLERGY", "ALLERGEN_TAG", MatchKind.HARD_FILTER),
			codeMap("DIET", "DIETARY_SUPPORT_TAG", MatchKind.HARD_FILTER),
			codeMap("MOBILITY", "ACCESSIBILITY_TAG", MatchKind.HARD_FILTER),
			codeMap("MOBILITY", "STAIRS_PRESENT", MatchKind.FLAG_COMPARE));

	// ── 알레르기 ──────────────────────────────────────────────────────────────

	/**
	 * 🔴 S15P21E201-1633(사용자 결정 2026-09-25) — 앱이 더 묻지 않는 알레르기의 옛 「반드시」 답은 빼는 조건이 아니라
	 * 경고다. 전에는 표식이 없는 곳이 「모름(반드시)」이 되어 후보 200곳이 전부 빠지고 다시 짜기가 실패했다.
	 */
	@Test
	@DisplayName("🔴 알레르기 표식이 없는 곳은 빼지 않고 「알레르기 확인 안 됨」 경고를 단다 — 다시 짜기가 실패하지 않게")
	void 미확인_알레르기는_경고로_남는다() {
		TripConstraint peanutAllergy = allergy("PEANUT");
		PlaceCandidateResponse.Candidate candidate = candidate(List.of()); // ALLERGEN_TAG 행 자체가 없다

		EngineCandidate result = score(candidate, null, List.of(peanutAllergy));

		assertThat(result.constraintVerdict()).isNotEqualTo(ConstraintVerdict.FAIL);
		assertThat(result.unknownFacts())
				.as("「모름(반드시)」이 남으면 후보 고르기가 뺀다")
				.noneSatisfy(fact -> assertThat(fact.get("fact")).isEqualTo("ALLERGEN_UNVERIFIED"));
		assertThat(result.warningCodes()).contains("ALLERGEN_UNVERIFIED");
		assertThat(result.preRankScore()).isNotNull();
	}

	@Test
	@DisplayName("🔴 REQUIRED 미확인은 등급까지 사실로 남긴다 — 제외 여부는 채점기가 아니라 임계값 설정이 정한다")
	void required_미확인은_등급까지_남긴다() {
		// 알레르기 대조표 줄이 없으면 「판정 못 함(반드시)」이다 — 표식이 없는 것과 달리 설정이 어긋난 것이다.
		TripConstraint peanutAllergy = allergy("PEANUT");
		PlaceCandidateResponse.Candidate candidate = candidate(List.of());

		EngineCandidate result = this.scorer.score(candidate, null, List.of(peanutAllergy), RADIUS_M, WEIGHTS,
				ALIGNMENT_WEIGHTS, this.preferenceCodeMap, List.of(), TasteWeightComponent.merge(List.of()),
				TASTE_MULTIPLIER);

		// 채점기가 점수를 지워서 후보를 빼지 않는다. unknown-exclusion-threshold 를 NONE 으로
		// 두는 것이 정당한 설정이라, 여기서 점수를 지우면 그 설정이 안 먹는다.
		assertThat(result.preRankScore()).isNotNull();
		assertThat(result.unknownFacts()).anySatisfy(fact ->
				assertThat(fact.get("severity")).isEqualTo("REQUIRED"));
	}

	@Test
	@DisplayName("있다고 확인된 알레르기 표식은 FAIL 이고 ALLERGEN_PRESENT 위반을 남긴다")
	void 확인된_알레르기는_FAIL이다() {
		TripConstraint peanutAllergy = allergy("PEANUT");
		PlaceCandidateResponse.Candidate candidate = candidate(
				List.of(tag("ALLERGEN_TAG", "PEANUT", "VERIFIED", "true")));

		EngineCandidate result = score(candidate, null, List.of(peanutAllergy));

		assertThat(result.constraintVerdict()).isEqualTo(ConstraintVerdict.FAIL);
		assertThat(result.violations()).anySatisfy(v -> {
			assertThat(v.get("code")).isEqualTo("ALLERGEN_PRESENT");
			assertThat(v.get("featureKey")).isEqualTo("PEANUT");
		});
	}

	@Test
	@DisplayName("확인된 해당 없음(값 false)은 통과다 — 다른 문제가 없으면 PASS")
	void 확인된_해당없음_알레르기는_통과다() {
		TripConstraint peanutAllergy = allergy("PEANUT");
		PlaceCandidateResponse.Candidate candidate = candidate(
				List.of(tag("ALLERGEN_TAG", "PEANUT", "VERIFIED", "false")));

		EngineCandidate result = score(candidate, null, List.of(peanutAllergy));

		assertThat(result.constraintVerdict()).isEqualTo(ConstraintVerdict.PASS);
		assertThat(result.violations()).isEmpty();
		assertThat(result.unknownFacts()).isEmpty();
	}

	// ── 식단 ──────────────────────────────────────────────────────────────────

	@Test
	@DisplayName("DIET REQUIRED — 지원 표식이 있다고 확인되면 통과다")
	void required_식단_지원확인되면_통과() {
		TripConstraint vegan = diet("VEGAN", TripConstraint.DietRequirement.REQUIRED);
		PlaceCandidateResponse.Candidate candidate = candidate(
				List.of(tag("DIETARY_SUPPORT_TAG", "VEGAN", "VERIFIED", "true")));

		EngineCandidate result = score(candidate, null, List.of(vegan));

		assertThat(result.constraintVerdict()).isEqualTo(ConstraintVerdict.PASS);
	}

	@Test
	@DisplayName("DIET REQUIRED — 지원 표식이 없다고 확인되면 FAIL 이고 DIET_NOT_SUPPORTED 를 남긴다")
	void required_식단_미지원확인되면_FAIL() {
		TripConstraint vegan = diet("VEGAN", TripConstraint.DietRequirement.REQUIRED);
		PlaceCandidateResponse.Candidate candidate = candidate(
				List.of(tag("DIETARY_SUPPORT_TAG", "VEGAN", "VERIFIED", "false")));

		EngineCandidate result = score(candidate, null, List.of(vegan));

		assertThat(result.constraintVerdict()).isEqualTo(ConstraintVerdict.FAIL);
		assertThat(result.violations()).anySatisfy(v -> assertThat(v.get("code")).isEqualTo("DIET_NOT_SUPPORTED"));
	}

	@Test
	@DisplayName("🔴 DIET REQUIRED — 미확인은 «탈락이 아니라 경고» 다 (S15P21E201-1468)")
	void required_식단_미확인이면_탈락이아니라경고() {
		// 이 시험이 생긴 이유. DIETARY_SUPPORT_TAG 가 운영에 0건이라, 식단을 고르기만 하면
		// 모든 후보가 unknownFacts 를 달고 unknown-exclusion-threshold(기본 REQUIRED)에
		// 전부 걸려 여행을 못 만들었다 — 실측으로 식단·알레르기를 고른 작업 8건이 8건 다
		// 실패했다. 팀이 식단만 「거르지 말고 확인 못 했다고 말한다」로 정했다.
		TripConstraint vegan = diet("VEGAN", TripConstraint.DietRequirement.REQUIRED);
		PlaceCandidateResponse.Candidate candidate = candidate(List.of());

		EngineCandidate result = score(candidate, null, List.of(vegan));

		assertThat(result.constraintVerdict()).as("미확인만으로 후보를 빼지 않는다")
				.isEqualTo(ConstraintVerdict.PASS);
		assertThat(result.unknownFacts()).as("여기 남으면 threshold 가 다시 빼 간다").isEmpty();
		assertThat(result.warningCodes()).contains("DIET_SUPPORT_UNVERIFIED");
	}

	@Test
	@DisplayName("🔴 경고 문자열은 앱 사전과 같은 이름이어야 한다 — 다르면 화면에서 조용히 사라진다")
	void 식단경고_문자열이_앱사전과_같다() {
		// describeWarningCodes 는 사전에 없는 코드를 건너뛴다. 이름만 바뀌어도 경고가
		// 안 뜨는데 오류는 안 난다 — 그래서 문자열 자체를 못 박는다.
		// 앱: frontend/src/plan/warningLabels.ts (S15P21E201-1503)
		assertThat(BaselineCandidateScorer.DIET_UNVERIFIED_WARNING).isEqualTo("DIET_SUPPORT_UNVERIFIED");
	}

	@Test
	@DisplayName("🔴 알레르기 재료가 들었다고 확인된 곳은 여전히 뺀다 — 경고로 돌리는 것은 「확인 안 됨」뿐이다")
	void 확인된_알레르기는_그대로_뺀다() {
		// 옛 팀 결정(「알레르기는 식단처럼 경고로 낮추지 않는다」)은 S15P21E201-1633 에서 「확인 안 됨」에 한해 바뀌었다.
		// 확인된 사실은 그대로다.
		TripConstraint peanut = allergy("PEANUT");
		PlaceCandidateResponse.Candidate candidate = candidate(List.of(tag("ALLERGEN_TAG", "PEANUT", "VERIFIED", "true")));

		EngineCandidate result = score(candidate, null, List.of(peanut));

		assertThat(result.constraintVerdict()).isEqualTo(ConstraintVerdict.FAIL);
		assertThat(result.warningCodes()).doesNotContain("ALLERGEN_UNVERIFIED");
	}

	@Test
	@DisplayName("알레르기 경고 낱말은 앱 사전의 이름 그대로다 — 바뀌면 화면에서 조용히 사라진다")
	void 알레르기_경고_낱말() {
		assertThat(BaselineCandidateScorer.ALLERGEN_UNVERIFIED_WARNING).isEqualTo("ALLERGEN_UNVERIFIED");
	}

	@Test
	@DisplayName("DIET REQUIRED — 「지원 안 함」이 확인되면 그대로 탈락이다 (경고로 낮추지 않는다)")
	void required_식단_확인된미지원은_그대로탈락() {
		TripConstraint vegan = diet("VEGAN", TripConstraint.DietRequirement.REQUIRED);
		PlaceCandidateResponse.Candidate candidate = candidate(
				List.of(tag("DIETARY_SUPPORT_TAG", "VEGAN", "VERIFIED", "false")));

		EngineCandidate result = score(candidate, null, List.of(vegan));

		assertThat(result.constraintVerdict()).as("아는 것은 거르는 게 맞다").isEqualTo(ConstraintVerdict.FAIL);
		assertThat(result.warningCodes()).doesNotContain("DIET_SUPPORT_UNVERIFIED");
	}

	@Test
	@DisplayName("DIET PREFERRED — 미확인이면 UNKNOWN 이고 severity 는 PREFERRED 다(REQUIRED 를 지어내지 않는다)")
	void preferred_식단_미확인이면_경고severity_preferred() {
		TripConstraint vegan = diet("VEGAN", TripConstraint.DietRequirement.PREFERRED);
		PlaceCandidateResponse.Candidate candidate = candidate(List.of());

		EngineCandidate result = score(candidate, null, List.of(vegan));

		assertThat(result.constraintVerdict()).isEqualTo(ConstraintVerdict.UNKNOWN);
		assertThat(result.unknownFacts()).anySatisfy(fact -> assertThat(fact.get("severity")).isEqualTo("PREFERRED"));
		// PREFERRED 미확인은 REQUIRED 가 아니므로 두 번째 안전장치가 점수를 지우지 않는다.
		assertThat(result.preRankScore()).isNotNull();
	}

	// ── 이동 ──────────────────────────────────────────────────────────────────

	@Test
	@DisplayName("MOBILITY — 검증된 접근 불가(확인된 해당 없음)는 FAIL 이고 ACCESS_VERIFIED_UNAVAILABLE 를 남긴다")
	void 검증된_접근불가는_FAIL이다() {
		TripConstraint wheelchair = mobility("WHEELCHAIR", TripConstraint.Severity.HARD);
		PlaceCandidateResponse.Candidate candidate = candidate(
				List.of(tag("ACCESSIBILITY_TAG", "WHEELCHAIR", "VERIFIED", "false")));

		EngineCandidate result = score(candidate, null, List.of(wheelchair));

		assertThat(result.constraintVerdict()).isEqualTo(ConstraintVerdict.FAIL);
		assertThat(result.violations())
				.anySatisfy(v -> assertThat(v.get("code")).isEqualTo("ACCESS_VERIFIED_UNAVAILABLE"));
	}

	@Test
	@DisplayName("🔴 MOBILITY 미확인은 FAIL 이 아니라 경고다 — S15P21E201-540")
	void 이동제약_미확인은_FAIL이_아니라_경고다() {
		TripConstraint stroller = mobility("STROLLER", TripConstraint.Severity.SOFT);
		PlaceCandidateResponse.Candidate candidate = candidate(List.of());

		EngineCandidate result = score(candidate, null, List.of(stroller));

		assertThat(result.constraintVerdict()).isNotEqualTo(ConstraintVerdict.FAIL);
		assertThat(result.warningCodes()).contains("ACCESSIBILITY_UNVERIFIED");
	}

	@Test
	@DisplayName("🔴 휠체어를 하드 제약으로 켜도 표식 없는 곳은 안 빠진다 — 이게 안 되면 추천이 0건이 된다")
	void 휠체어_하드제약이어도_표식없는_곳은_안빠진다() {
		// 접근성 표식이 붙은 장소는 운영 실측에서 4% 뿐이다. 미확인을 탈락으로 세면 96% 가
		// 사라지고 반경 조건까지 겹치면 결과가 0건이 된다.
		TripConstraint wheelchair = mobility("WHEELCHAIR", TripConstraint.Severity.HARD);
		PlaceCandidateResponse.Candidate candidate = candidate(List.of());

		EngineCandidate result = score(candidate, null, List.of(wheelchair));

		assertThat(result.constraintVerdict()).isNotEqualTo(ConstraintVerdict.FAIL);
		assertThat(result.violations()).isEmpty();
		// severity 를 REQUIRED 로 단 미확인 사실이 남으면 임계값 설정(기본 REQUIRED)이 그
		// 후보를 뺀다. 그래서 그 자리가 비어 있어야 한다.
		assertThat(result.unknownFacts())
				.noneSatisfy(fact -> assertThat(fact.get("fact")).isEqualTo("ACCESSIBILITY_UNVERIFIED"));
		assertThat(result.preRankScore()).as("점수가 null 이면 후보에서 빠진다").isNotNull();
		assertThat(result.warningCodes()).contains("ACCESSIBILITY_UNVERIFIED");
	}

	// ── 이동 조건을 경사로 (S15P21E201-1625) ───────────────────────────────────

	/** 경사 표식 — {@code place-slope-by-id} 가 적재하는 모양 그대로({@code score} 에 %). */
	private static PlaceFeatureView slope(double percent) {
		return scoreFeature("SLOPE_PERCENT", "ESTIMATED", "{\"score\": " + percent + ", \"stat\": \"p50\"}");
	}

	@Test
	@DisplayName("🔴 유아차를 「반드시」로 고르면 경사가 상한(8.33%)을 넘는 곳은 빠진다 — 산이 경고만 달고 들어가던 것")
	void 반드시면_가파른_곳은_빠진다() {
		for (String key : List.of("STROLLER", "WHEELCHAIR", "HEAVY_LUGGAGE")) {
			EngineCandidate result = score(candidateOf("NATURE_WALK", List.of(slope(16.0))), null,
					List.of(mobility(key, TripConstraint.Severity.HARD)));

			assertThat(result.constraintVerdict()).as(key).isEqualTo(ConstraintVerdict.FAIL);
			assertThat(result.violations()).as(key)
					.anySatisfy(v -> assertThat(v.get("code")).isEqualTo("SLOPE_OVER_LIMIT"));
		}
	}

	@Test
	@DisplayName("상한 아래면 안 빠지고, 미확인 경고는 그대로 남는다 — 경사는 추정값이라 「갈 수 있음」을 약속하지 않는다")
	void 완만하면_남고_미확인_경고는_그대로다() {
		EngineCandidate result = score(candidateOf("NATURE_WALK", List.of(slope(2.8))), null,
				List.of(mobility("STROLLER", TripConstraint.Severity.HARD)));

		assertThat(result.constraintVerdict()).isNotEqualTo(ConstraintVerdict.FAIL);
		assertThat(result.warningCodes()).contains("ACCESSIBILITY_UNVERIFIED").doesNotContain("SLOPE_OVER_LIMIT");
	}

	@Test
	@DisplayName("「되도록」이면 가팔라도 빼지 않고 경고만 단다")
	void 되도록이면_경고만() {
		EngineCandidate result = score(candidateOf("NATURE_WALK", List.of(slope(16.0))), null,
				List.of(mobility("STROLLER", TripConstraint.Severity.SOFT)));

		assertThat(result.constraintVerdict()).isNotEqualTo(ConstraintVerdict.FAIL);
		assertThat(result.warningCodes()).contains("SLOPE_OVER_LIMIT");
	}

	@Test
	@DisplayName("🔴 확인된 「갈 수 있음」 표식이 있으면 경사가 높아도 통과한다 — 확인된 사실이 추정값보다 앞선다")
	void 확인된_표식이_경사보다_앞선다() {
		EngineCandidate result = score(candidateOf("NATURE_WALK", List.of(
				tag("ACCESSIBILITY_TAG", "STROLLER", "VERIFIED", "true"), slope(11.4))), null,
				List.of(mobility("STROLLER", TripConstraint.Severity.HARD)));

		assertThat(result.constraintVerdict()).isNotEqualTo(ConstraintVerdict.FAIL);
		assertThat(result.violations()).isEmpty();
	}

	@Test
	@DisplayName("상한은 설정값 하나다 — 12% 로 올리면 10% 인 곳은 안 빠진다")
	void 상한은_설정값이다() {
		BaselineCandidateScorer lenient = new BaselineCandidateScorer(new ObjectMapper(), 12.0);

		EngineCandidate result = lenient.score(candidateOf("CULTURE_TEMPLE", List.of(slope(10.0))), null,
				List.of(mobility("STROLLER", TripConstraint.Severity.HARD)), RADIUS_M, WEIGHTS, ALIGNMENT_WEIGHTS,
				this.preferenceCodeMap, this.constraintCodeMap, TasteWeightComponent.merge(List.of()), TASTE_MULTIPLIER);

		assertThat(result.constraintVerdict()).isNotEqualTo(ConstraintVerdict.FAIL);
	}

	@Test
	@DisplayName("🔴 안 재 본 곳은 재 보고 갈 수 있는 곳보다 뒤로 밀린다 — 빼지 않는 대신 감점한다")
	void 미확인은_확인된_곳보다_점수가_낮다() {
		TripConstraint wheelchair = mobility("WHEELCHAIR", TripConstraint.Severity.HARD);

		EngineCandidate verified = score(
				candidate(List.of(tag("ACCESSIBILITY_TAG", "WHEELCHAIR", "VERIFIED", "true"))),
				null, List.of(wheelchair));
		EngineCandidate unverified = score(candidate(List.of()), null, List.of(wheelchair));

		assertThat(unverified.preRankScore())
				.as("미확인이 확인된 곳과 같은 점수면 뒤로 밀리지 않는다")
				.isLessThan(verified.preRankScore());
	}

	@Test
	@DisplayName("STAIRS_AVOIDANCE — 계단이 있다고 확인돼도 FAIL 이 아니라 경고 STAIRS_PRESENT 만 남긴다")
	void 계단회피_계단있으면_FAIL아니고_경고만() {
		TripConstraint stairsAvoidance = mobility("STAIRS_AVOIDANCE", TripConstraint.Severity.SOFT);
		PlaceCandidateResponse.Candidate candidate = candidate(
				List.of(scoreFeature("STAIRS_PRESENT", "VERIFIED", "true")));

		EngineCandidate result = score(candidate, null, List.of(stairsAvoidance));

		assertThat(result.constraintVerdict()).isNotEqualTo(ConstraintVerdict.FAIL);
		assertThat(result.warningCodes()).contains("STAIRS_PRESENT");
	}

	@Test
	@DisplayName("MAX_WALKING_METERS — 거리 초과는 FAIL 이 아니라 경고 WALKING_OVER_LIMIT 만 남긴다")
	void 보행상한_초과는_FAIL아니고_경고만() {
		TripConstraint maxWalking = mobilityWithThreshold("MAX_WALKING_METERS", 300.0);
		PlaceCandidateResponse.Candidate candidate = candidate(500L, List.of());

		EngineCandidate result = score(candidate, null, List.of(maxWalking));

		assertThat(result.constraintVerdict()).isNotEqualTo(ConstraintVerdict.FAIL);
		assertThat(result.warningCodes()).contains("WALKING_OVER_LIMIT");
	}

	// ── 점수 ──────────────────────────────────────────────────────────────────

	@Test
	@DisplayName("거리는 항상 NEAR_ORIGIN 을 남기고, 못 구한 피처는 0 이 아니라 null 이다")
	void 거리는_항상_계산되고_못구한_피처는_null() {
		PlaceCandidateResponse.Candidate candidate = candidate(1000L, List.of());

		EngineCandidate result = score(candidate, null, List.of());

		assertThat(result.reasonCodes()).contains("NEAR_ORIGIN");
		assertThat(result.featureValues().get("distanceM")).isEqualTo(1000L);
		// 취향 스냅샷 자체가 없으니 관심 태그 겹침을 잴 수 없다 — 0 이 아니라 null.
		assertThat(result.featureValues().get("interestTagOverlap")).isNull();
	}

	@Test
	@DisplayName("관심 태그가 겹치면 TAG_MATCH_INTEREST 와 겹침 비율을 남긴다")
	void 관심태그_겹치면_리즌코드와_비율() {
		// 두 번째 코드는 이 후보의 갈래(CAFE)가 아니어야 한다 — 갈래가 맞으면 아래 테마 규칙으로 만점이 된다.
		PreferenceSnapshot snapshot = snapshot("CATEGORY", "{\"codes\": [\"SEA\", \"PARK\"]}");
		PlaceCandidateResponse.Candidate candidate = candidate(
				List.of(tag("INTEREST_TAG", "SEA", "VERIFIED", null)));

		EngineCandidate result = score(candidate, snapshot, List.of());

		assertThat(result.reasonCodes()).contains("TAG_MATCH_INTEREST");
		assertThat((Double) result.featureValues().get("interestTagOverlap")).isEqualTo(0.5);
	}

	@Test
	@DisplayName("🔴 여행 테마가 장소 갈래(place.category)와 같으면 태그가 없어도 관심 만점 — 앱 테마와 관심 태그는 어휘가 다르다")
	void 테마가_장소갈래와_같으면_관심_만점() {
		// 운영 관심 태그는 NATURE·WALK·TRADITIONAL_MARKET … 이고 앱 테마는 SEA_BEACH·FOOD … 다(2026-09-23 실측).
		// 태그만 보면 테마가 아무 데도 안 겹쳐 가산이 0 이었다.
		PreferenceSnapshot snapshot = snapshot("CATEGORY", "[\"SEA_BEACH\", \"FOOD\"]");
		PlaceCandidateResponse.Candidate beach = candidateOf("SEA_BEACH", List.of());

		EngineCandidate result = score(beach, snapshot, List.of());

		assertThat(result.reasonCodes()).contains("TAG_MATCH_INTEREST");
		// 테마를 둘 골랐어도 반점이 아니다 — 장소는 갈래가 하나다.
		assertThat((Double) result.featureValues().get("interestTagOverlap")).isEqualTo(1.0);
	}

	@Test
	@DisplayName("테마와 갈래가 다르면 가산이 없다 — 고른 갈래가 위로 올라오는 것은 이 차이 때문이다")
	void 테마와_갈래가_다르면_가산없음() {
		PreferenceSnapshot snapshot = snapshot("CATEGORY", "[\"SEA_BEACH\"]");

		EngineCandidate beach = score(candidateOf("SEA_BEACH", List.of()), snapshot, List.of());
		EngineCandidate restaurant = score(candidateOf("FOOD", List.of()), snapshot, List.of());

		assertThat((Double) restaurant.featureValues().get("interestTagOverlap")).isEqualTo(0.0);
		assertThat(beach.preRankScore()).isGreaterThan(restaurant.preRankScore());
	}

	// ── 도구 ──────────────────────────────────────────────────────────────────

	@Test
	@DisplayName("🔴 대조표에 알레르기 줄이 없으면 조용히 통과시키지 않는다 — 판정 못 한 것은 판정할 필요가 없는 것과 다르다")
	void 대조표가_비면_알레르기는_통과가_아니다() {
		TripConstraint peanutAllergy = allergy("PEANUT");
		// 표식은 "없다고 확인됨" 이라 대조표만 있으면 통과했을 후보다.
		PlaceCandidateResponse.Candidate candidate =
				candidate(List.of(tag("ALLERGEN_TAG", "PEANUT", "VERIFIED", "false")));

		EngineCandidate result = this.scorer.score(candidate, null, List.of(peanutAllergy), RADIUS_M, WEIGHTS,
				ALIGNMENT_WEIGHTS,
				this.preferenceCodeMap, List.of(), List.of(), TASTE_MULTIPLIER);

		assertThat(result.constraintVerdict()).isEqualTo(ConstraintVerdict.UNKNOWN);
		assertThat(result.unknownFacts()).anySatisfy(fact -> {
			assertThat(fact.get("fact")).isEqualTo("ALLERGEN_MAPPING_MISSING");
			assertThat(fact.get("severity")).isEqualTo("REQUIRED");
		});
	}

	/**
	 * 벡터 없는 계정의 점수가 같다는 것만 검사하면, 배관이 끊겨 기여가 영원히 0 이어도
	 * 통과한다. 그래서 겹치는 것이 있을 때 기여가 0 이 아닌지를 함께 본다.
	 */
	@Test
	@DisplayName("🔴 벡터가 겹치면 덧점수가 실제로 붙는다 — 기여가 0 이 아니다")
	void tasteVectorAddsWhenItOverlaps() {
		PlaceCandidateResponse.Candidate cafe = candidate(List.of(tag("INTEREST_TAG", "CAFE_HEALING", "VERIFIED", "true")));
		// 🔴 예전에는 무게 1.0 을 넣었는데 그건 운영에서 나올 수 없는 값이다 (S15P21E201-1500).
		//    행동 무게는 관측이 아무리 쌓여도 1 에 못 닿는다 — raw/(|raw|+K) 는 늘 1 미만이다.
		List<UserTasteWeight> vector = List.of(UserTasteWeight.fromInteraction(TASTE_VECTOR_ID,
				TasteDimension.CATEGORY, "CAFE_HEALING", rawForWeight(0.75), 7, NOW));

		EngineCandidate without = score(cafe, null, List.of());
		EngineCandidate with = score(cafe, null, List.of(), vector);

		assertThat(with.preRankScore()).as("겹쳤는데 점수가 안 움직이면 배관이 끊긴 것이다")
				.isGreaterThan(without.preRankScore());
		assertThat(with.preRankScore() - without.preRankScore()).isCloseTo(TASTE_MULTIPLIER * 0.75, within(1e-9));
		assertThat(with.scoreComponents()).containsKey("tasteVectorContribution");
	}

	/** 벡터가 없는 사람이 대부분이라, 그 사람들의 점수는 한 톨도 달라지면 안 된다. */
	@Test
	@DisplayName("🔴 벡터가 없으면 점수가 한 톨도 안 바뀐다")
	void noVectorMeansNoChange() {
		PlaceCandidateResponse.Candidate cafe = candidate(List.of(tag("INTEREST_TAG", "CAFE_HEALING", "VERIFIED", "true")));

		EngineCandidate empty = score(cafe, null, List.of(), List.of());
		EngineCandidate legacy = score(cafe, null, List.of());

		assertThat(empty.preRankScore()).isEqualTo(legacy.preRankScore());
		// 겹친 게 없다(0.0)와 잴 것이 없다(null)를 구분한다.
		assertThat(empty.featureValues()).containsEntry("tasteVectorOverlap", null);
	}

	/** 태그 겹침은 맞은 개수라 언제나 0 이상이지만, 싫어하는 갈래는 점수를 내려야 한다. */
	@Test
	@DisplayName("🔴 음수 성분이면 점수가 내려간다 — 겹침 개수로는 못 하는 일")
	void negativeWeightLowersScore() {
		PlaceCandidateResponse.Candidate cafe = candidate(List.of(tag("INTEREST_TAG", "CAFE_HEALING", "VERIFIED", "true")));
		List<UserTasteWeight> dislike = List.of(
				UserTasteWeight.fromInteraction(TASTE_VECTOR_ID, TasteDimension.CATEGORY, "CAFE_HEALING",
						rawForWeight(-0.75), 7, NOW));

		EngineCandidate without = score(cafe, null, List.of());
		EngineCandidate with = score(cafe, null, List.of(), dislike);

		assertThat(with.preRankScore()).isLessThan(without.preRankScore());
	}

	/** 벡터는 있는데 이 후보와 안 겹치면 기여는 0 이다 — null 이 아니다. 잴 것은 있었다. */
	@Test
	@DisplayName("벡터가 있어도 안 겹치면 기여는 0 이다")
	void vectorWithoutOverlapContributesZero() {
		PlaceCandidateResponse.Candidate notCafe = candidate(List.of(tag("INTEREST_TAG", "FOOD", "VERIFIED", "true")));
		List<UserTasteWeight> vector = List.of(
				UserTasteWeight.fromInteraction(TASTE_VECTOR_ID, TasteDimension.CATEGORY, "CAFE_HEALING",
						rawForWeight(0.75), 7, NOW));

		EngineCandidate without = score(notCafe, null, List.of());
		EngineCandidate with = score(notCafe, null, List.of(), vector);

		assertThat(with.preRankScore()).isEqualTo(without.preRankScore());
		assertThat(with.featureValues()).containsEntry("tasteVectorOverlap", 0.0);
	}

	/**
	 * 설문만으로 접힌 성분은 이 항에 안 들어온다. 그 답은 {@code applyTagComponent} 의 CATEGORY
	 * 태그 겹침이 이미 채점했으므로, 여기서 또 더하면 같은 설문을 배수만큼 한 번 더 세는 것이 된다.
	 *
	 * <p>잴 것이 아예 없는 것과 같은 자리라 {@code tasteVectorOverlap} 은 {@code null} 이다 —
	 * 0.0(겹친 게 없다)과 구분한다.
	 */
	@Test
	@DisplayName("🔴 설문만으로 접힌 성분은 덧점수에 안 들어간다 — 설문을 두 번 세지 않는다")
	void surveyOnlyWeightsDoNotCountTwice() {
		PlaceCandidateResponse.Candidate cafe = candidate(List.of(tag("INTEREST_TAG", "CAFE_HEALING", "VERIFIED", "true")));
		List<UserTasteWeight> surveyOnly = List.of(
				UserTasteWeight.fromSurvey(TASTE_VECTOR_ID, TasteDimension.CATEGORY, "CAFE_HEALING", 1.0, NOW));

		EngineCandidate without = score(cafe, null, List.of());
		EngineCandidate with = score(cafe, null, List.of(), surveyOnly);

		assertThat(with.preRankScore()).isEqualTo(without.preRankScore());
		assertThat(with.featureValues()).containsEntry("tasteVectorOverlap", null);
	}

	/**
	 * 설문과 행동이 섞여 있으면 행동 쪽만 세고, 분모도 그 개수다.
	 *
	 * <p>🔴 이 시험이 {@code CAFE_HEALING} 을 <b>설문 행 + 행동 행 두 줄</b>로 준다
	 * (S15P21E201-1499). 예전에는 그 자리가 {@code BLENDED} 한 줄이었고 값이 1.0 이었는데,
	 * 합치는 규칙이 합이라 {@code 0.4 + 0.6} 으로 나눠 적어도 합쳐 보면 같은 1.0 이다.
	 * <b>기대값이 그대로인 것이 「나눠 적어도 점수가 안 바뀐다」의 증명이다.</b>
	 */
	@Test
	@DisplayName("설문과 행동이 섞이면 행동 성분만 더한다 — 나눠 적어도 합친 값은 같다")
	void blendsCountOnlyBehaviourBackedComponents() {
		PlaceCandidateResponse.Candidate cafe = candidate(List.of(tag("INTEREST_TAG", "CAFE_HEALING", "VERIFIED", "true")));
		List<UserTasteWeight> mixed = List.of(
				UserTasteWeight.fromSurvey(TASTE_VECTOR_ID, TasteDimension.CATEGORY, "SEA_BEACH", 1.0, NOW),
				UserTasteWeight.fromSurvey(TASTE_VECTOR_ID, TasteDimension.CATEGORY, "CAFE_HEALING", 0.4, NOW),
				UserTasteWeight.fromInteraction(TASTE_VECTOR_ID, TasteDimension.CATEGORY, "CAFE_HEALING",
						rawForWeight(0.6), 3, NOW));

		EngineCandidate without = score(cafe, null, List.of());
		EngineCandidate with = score(cafe, null, List.of(), mixed);

		// 분모가 걸러낸 뒤의 성분 수(1)라, 하나 맞으면 비율이 1.0 이다. 설문 성분까지 분모에
		// 넣으면 0.5 가 되어 「설문을 많이 답할수록 행동 신호가 묽어지는」 값이 된다.
		assertThat(with.preRankScore() - without.preRankScore()).isCloseTo(TASTE_MULTIPLIER * 1.0, within(1e-9));
	}

	private EngineCandidate score(PlaceCandidateResponse.Candidate candidate, PreferenceSnapshot snapshot,
			List<TripConstraint> constraints) {
		return score(candidate, snapshot, constraints, List.of());
	}

	/**
	 * 취향 벡터를 함께 넘기는 갈래.
	 *
	 * <p>저장된 행을 그대로 넘기지 않고 {@link TasteWeightComponent#merge(List)} 를 거친다 —
	 * 운영에서 {@code BaselineRecommendationEngine} 이 읽어 넘길 때 하는 것과 같다. 시험이
	 * 그 단계를 건너뛰면 「나눠 적어도 점수가 같다」를 증명하지 못한다 (S15P21E201-1499).
	 */
	private EngineCandidate score(PlaceCandidateResponse.Candidate candidate, PreferenceSnapshot snapshot,
			List<TripConstraint> constraints, List<UserTasteWeight> tasteWeights) {
		return this.scorer.score(candidate, snapshot, constraints, RADIUS_M, WEIGHTS, ALIGNMENT_WEIGHTS,
				this.preferenceCodeMap,
				this.constraintCodeMap, TasteWeightComponent.merge(tasteWeights), TASTE_MULTIPLIER);
	}

	/**
	 * 원하는 «무게» 에서 {@code raw} 를 역산한다 (S15P21E201-1500).
	 *
	 * <p>{@code fromInteraction} 은 이제 눌러 담기 전의 합을 받는다 —
	 * {@code weight = raw/(|raw|+K)} 이므로 {@code raw = K*w/(1-|w|)} 이다. 시험은 무게로
	 * 말하는 편이 읽히므로 여기서 되돌린다.
	 *
	 * <p>🔴 {@code w = ±1} 은 못 넣는다. 나누는 값이 0 이 되는데, <b>그것이 사실이다</b> —
	 * 행동 무게는 관측이 아무리 쌓여도 1 에 못 닿는다.
	 */
	private static double rawForWeight(double weight) {
		return (UserTasteWeight.CONFIDENCE_K * weight) / (1.0 - Math.abs(weight));
	}

	private static PlaceCandidateResponse.Candidate candidate(List<PlaceFeatureView> features) {
		return candidate(1000L, features);
	}

	private static PlaceCandidateResponse.Candidate candidateOf(String category, List<PlaceFeatureView> features) {
		return new PlaceCandidateResponse.Candidate(UUID.randomUUID(), "테스트 장소", category, 35.1, 129.0,
				1000L, features);
	}

	private static PlaceCandidateResponse.Candidate candidate(long distanceM, List<PlaceFeatureView> features) {
		return new PlaceCandidateResponse.Candidate(UUID.randomUUID(), "테스트 장소", "CAFE", 35.1, 129.0,
				distanceM, features);
	}

	private static PlaceFeatureView tag(String featureType, String featureKey, String evidenceStatus, String raw) {
		return new PlaceFeatureView(featureType, featureKey, evidenceStatus, jsonOf(raw), null, "FIXTURE");
	}

	private static PlaceFeatureView scoreFeature(String featureType, String evidenceStatus, String raw) {
		return new PlaceFeatureView(featureType, null, evidenceStatus, jsonOf(raw), null, "FIXTURE");
	}

	private static tools.jackson.databind.JsonNode jsonOf(String raw) {
		if (raw == null) {
			return null;
		}
		return new ObjectMapper().readTree(raw);
	}

	private static PreferenceSnapshot snapshot(String dimension, String valueJson) {
		return new PreferenceSnapshot(UUID.randomUUID().toString(), UUID.randomUUID().toString(), 1,
				List.of(new PreferenceSnapshot.PreferenceAnswer(dimension, valueJson,
						PreferenceSnapshot.AnswerStatus.SELECTED)),
				PersonalizationScope.TRIP, List.of(), Instant.now());
	}

	private static TripConstraint allergy(String code) {
		return new TripConstraint(UUID.randomUUID().toString(), "trip-1", "ALLERGY", code,
				TripConstraint.Severity.HARD, "EXCLUDES", null, null, TripConstraint.EvidenceStatus.VERIFIED,
				TripConstraint.AnswerStatus.SELECTED, PersonalizationScope.TRIP, null);
	}

	private static TripConstraint diet(String code, TripConstraint.DietRequirement requirement) {
		TripConstraint.Severity severity = (requirement == TripConstraint.DietRequirement.REQUIRED)
				? TripConstraint.Severity.HARD : TripConstraint.Severity.SOFT;
		String operator = (severity == TripConstraint.Severity.HARD) ? "EXCLUDES" : null;
		return new TripConstraint(UUID.randomUUID().toString(), "trip-1", "DIET", code, severity, operator, null,
				null, TripConstraint.EvidenceStatus.VERIFIED, TripConstraint.AnswerStatus.SELECTED,
				PersonalizationScope.TRIP, requirement);
	}

	private static TripConstraint mobility(String key, TripConstraint.Severity severity) {
		String operator = (severity == TripConstraint.Severity.HARD) ? "EXCLUDES" : null;
		return new TripConstraint(UUID.randomUUID().toString(), "trip-1", "MOBILITY", key, severity, operator,
				"true", null, TripConstraint.EvidenceStatus.VERIFIED, TripConstraint.AnswerStatus.SELECTED,
				PersonalizationScope.TRIP, null);
	}

	private static TripConstraint mobilityWithThreshold(String key, double thresholdMeters) {
		return new TripConstraint(UUID.randomUUID().toString(), "trip-1", "MOBILITY", key,
				TripConstraint.Severity.SOFT, null, null, thresholdMeters, TripConstraint.EvidenceStatus.VERIFIED,
				TripConstraint.AnswerStatus.SELECTED, PersonalizationScope.TRIP, null);
	}

	private static UserPlaceCodeMap codeMap(String userInputCode, String placeFeatureType, MatchKind matchKind) {
		UserPlaceCodeMap row = mock(UserPlaceCodeMap.class);
		when(row.getUserInputCode()).thenReturn(userInputCode);
		when(row.getPlaceFeatureType()).thenReturn(placeFeatureType);
		when(row.getMatchKind()).thenReturn(matchKind);
		return row;
	}
}
