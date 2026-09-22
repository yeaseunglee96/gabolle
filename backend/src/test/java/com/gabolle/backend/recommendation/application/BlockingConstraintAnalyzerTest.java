package com.gabolle.backend.recommendation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.recommendation.domain.CandidateStage;
import com.gabolle.backend.recommendation.domain.ConstraintVerdict;
import com.gabolle.backend.recommendation.domain.FallbackMode;
import com.gabolle.backend.recommendation.domain.JobStage;
import com.gabolle.backend.recommendation.domain.JobType;
import com.gabolle.backend.recommendation.domain.RecommendationCandidate;
import com.gabolle.backend.recommendation.domain.RecommendationJob;
import com.gabolle.backend.recommendation.repository.RecommendationCandidateRepository;

import tools.jackson.databind.ObjectMapper;

/**
 * 「어느 조건이 후보를 다 걷어냈나」 — S15P21E201-1468 의 ㄷ.
 *
 * <p>이 시험이 생긴 이유. 알레르기·식단·이동 중 하나가 후보를 전부 걷어내면 작업은 실패하는데
 * 화면은 「조건을 조정해 주세요」라고만 말했다. 사용자는 <b>어느 조건인지 모른 채</b> 날짜나
 * 예산을 넓혀 보고 또 실패한다 — 그 둘은 이 실패와 아무 상관이 없다.
 *
 * <p>🔴 그리고 <b>「맞는 곳이 없다」와 「확인하지 못했다」를 가르는 것</b>이 이 클래스의 요점이다.
 * 운영에 {@code ALLERGEN_TAG} 가 0건이라 지금 나오는 것은 전부 뒤쪽이다 — 서버는 위험한 곳을
 * 골라낸 것이 아니라 안전한지 확인할 수 없어 전부 뺐다.
 */
class BlockingConstraintAnalyzerTest {

	private RecommendationCandidateRepository candidateRepository;
	private BlockingConstraintAnalyzer analyzer;

	private final UUID requestId = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		this.candidateRepository = mock(RecommendationCandidateRepository.class);
		this.analyzer = new BlockingConstraintAnalyzer(this.candidateRepository, new ObjectMapper());
	}

	private RecommendationJob failedJob(String errorCode, JobStage stage) {
		RecommendationJob job = RecommendationJob.start(UUID.randomUUID(), this.requestId, UUID.randomUUID(),
				JobType.ITINERARY_GENERATION, OffsetDateTime.now());
		job.markFailed(errorCode, stage, OffsetDateTime.now(), false, false);
		return job;
	}

	private RecommendationJob noFeasibleJob() {
		return failedJob(RecommendationCodes.ERROR_NO_FEASIBLE_RESULT, JobStage.CONSTRAINT_EVALUATION);
	}

	private RecommendationCandidate candidate(String unknownFacts, String violations) {
		return RecommendationCandidate.builder()
				.candidateId(UUID.randomUUID()).requestId(this.requestId).placeId(UUID.randomUUID())
				.candidateSource("BASELINE").candidateStage(CandidateStage.QUALITY_FILTERED)
				.eligible(false).constraintVerdict(ConstraintVerdict.UNKNOWN)
				.unknownFacts(unknownFacts).violations(violations)
				.returned(false).fallbackMode(FallbackMode.BASELINE)
				.createdAt(OffsetDateTime.now())
				.build();
	}

	private void given(RecommendationCandidate... candidates) {
		when(this.candidateRepository.findByRequestIdOrderByFinalRankAscPlaceIdAsc(this.requestId))
				.thenReturn(List.of(candidates));
	}

	@Test
	@DisplayName("🔴 땅콩 알레르기가 후보를 다 걷어냈으면 그 조건을 이름으로 말한다")
	void namesTheAllergyThatBlockedEverything() {
		// 운영에서 실제로 나온 모양 그대로 — 후보 200건이 전부 이 하나에 걸렸다(2026-09-22).
		String peanut = "[{\"fact\":\"ALLERGEN_UNVERIFIED\",\"featureKey\":\"PEANUT\",\"severity\":\"REQUIRED\"}]";
		given(candidate(peanut, "[]"), candidate(peanut, "[]"), candidate(peanut, "[]"));

		List<BlockingConstraintAnalyzer.Blocking> blocking = this.analyzer.analyze(noFeasibleJob());

		assertThat(blocking).hasSize(1);
		assertThat(blocking.get(0).constraintType()).isEqualTo("ALLERGY");
		assertThat(blocking.get(0).constraintKey()).isEqualTo("PEANUT");
		assertThat(blocking.get(0).blockedCandidates()).isEqualTo(3);
	}

	@Test
	@DisplayName("🔴 「확인 못 했다」와 「확인했고 안 된다」를 가른다 — 같은 말로 쓰면 부산에 먹을 게 없다고 읽힌다")
	void separatesUnverifiedFromVerifiedUnavailable() {
		given(candidate("[{\"fact\":\"ALLERGEN_UNVERIFIED\",\"featureKey\":\"PEANUT\"}]", "[]"),
				candidate("[]", "[{\"code\":\"ALLERGEN_PRESENT\",\"featureKey\":\"SHRIMP\"}]"));

		List<BlockingConstraintAnalyzer.Blocking> blocking = this.analyzer.analyze(noFeasibleJob());

		assertThat(blocking).extracting(BlockingConstraintAnalyzer.Blocking::constraintKey,
				BlockingConstraintAnalyzer.Blocking::reason)
				.containsExactlyInAnyOrder(
						org.assertj.core.groups.Tuple.tuple("PEANUT", BlockingConstraintAnalyzer.REASON_UNVERIFIED),
						org.assertj.core.groups.Tuple.tuple("SHRIMP", BlockingConstraintAnalyzer.REASON_VIOLATED));
	}

	@Test
	@DisplayName("많이 막은 조건이 먼저 온다 — 순서가 흔들리면 화면 문구가 새로고침마다 바뀐다")
	void mostBlockingComesFirst() {
		String diet = "[{\"fact\":\"DIET_SUPPORT_UNVERIFIED\",\"featureKey\":\"VEGAN\"}]";
		String both = "[{\"fact\":\"DIET_SUPPORT_UNVERIFIED\",\"featureKey\":\"VEGAN\"},"
				+ "{\"fact\":\"ACCESSIBILITY_MAPPING_MISSING\",\"featureKey\":\"WHEELCHAIR\"}]";
		given(candidate(diet, "[]"), candidate(diet, "[]"), candidate(both, "[]"));

		List<BlockingConstraintAnalyzer.Blocking> blocking = this.analyzer.analyze(noFeasibleJob());

		assertThat(blocking).hasSize(2);
		assertThat(blocking.get(0).constraintKey()).isEqualTo("VEGAN");
		assertThat(blocking.get(0).blockedCandidates()).isEqualTo(3);
		assertThat(blocking.get(1).constraintKey()).isEqualTo("WHEELCHAIR");
		assertThat(blocking.get(1).constraintType()).isEqualTo("MOBILITY");
	}

	@Test
	@DisplayName("한 후보가 같은 조건에 두 번 걸려도 한 번만 센다 — 막은 수가 후보 수를 넘으면 안 된다")
	void oneCandidateCountsOnce() {
		String twice = "[{\"fact\":\"ALLERGEN_UNVERIFIED\",\"featureKey\":\"PEANUT\"},"
				+ "{\"fact\":\"ALLERGEN_UNVERIFIED\",\"featureKey\":\"PEANUT\"}]";
		given(candidate(twice, "[]"));

		assertThat(this.analyzer.analyze(noFeasibleJob()).get(0).blockedCandidates()).isEqualTo(1);
	}

	@Test
	@DisplayName("🔴 설명할 수 없는 실패는 빈 목록이고 질의조차 안 한다 — 지어내지 않는다")
	void otherFailuresExplainNothing() {
		// 다른 코드로 실패했다.
		assertThat(this.analyzer.analyze(failedJob("ENGINE_NOT_CONFIGURED", JobStage.CANDIDATE_GENERATION)))
				.isEmpty();
		// 같은 코드지만 후보를 아예 못 만든 단계다 — 조건 탓이 아니다.
		assertThat(this.analyzer.analyze(
				failedJob(RecommendationCodes.ERROR_NO_FEASIBLE_RESULT, JobStage.CANDIDATE_GENERATION)))
				.isEmpty();

		verify(this.candidateRepository, never()).findByRequestIdOrderByFinalRankAscPlaceIdAsc(any());
	}

	@Test
	@DisplayName("성공한 작업은 빈 목록이다")
	void succeededJobExplainsNothing() {
		RecommendationJob job = RecommendationJob.start(UUID.randomUUID(), this.requestId, UUID.randomUUID(),
				JobType.ITINERARY_GENERATION, OffsetDateTime.now());
		job.markCompleted(OffsetDateTime.now(), OffsetDateTime.now(), FallbackMode.BASELINE, null);

		assertThat(this.analyzer.analyze(job)).isEmpty();
		verify(this.candidateRepository, never()).findByRequestIdOrderByFinalRankAscPlaceIdAsc(any());
	}

	@Test
	@DisplayName("값이 깨진 후보는 그 하나만 건너뛴다 — 설명 전체를 포기하지 않는다")
	void brokenJsonSkipsOnlyThatCandidate() {
		given(candidate("{이건 JSON 이 아니다", "[]"),
				candidate("[{\"fact\":\"ALLERGEN_UNVERIFIED\",\"featureKey\":\"PEANUT\"}]", "[]"));

		List<BlockingConstraintAnalyzer.Blocking> blocking = this.analyzer.analyze(noFeasibleJob());

		assertThat(blocking).hasSize(1);
		assertThat(blocking.get(0).constraintKey()).isEqualTo("PEANUT");
	}

	@Test
	@DisplayName("🔴 모르는 코드는 갈래도 사유도 지어내지 않는다 — 코드 자체는 잃지 않는다")
	void unknownCodeInventsNothingButKeepsItsRawCode() {
		given(candidate("[{\"fact\":\"SOMETHING_NEW\",\"featureKey\":\"X\"}]", "[]"));

		BlockingConstraintAnalyzer.Blocking blocking = this.analyzer.analyze(noFeasibleJob()).get(0);

		assertThat(blocking.constraintType()).as("모르는 갈래를 지어내지 않는다").isNull();
		// 사유를 임의로 채우면 확인된 위반이 「확인 못 함」으로 둔갑할 수 있다 — 이 구분이
		// 이 기능의 전부라, 모르면 말하지 않는 쪽이 맞다.
		assertThat(blocking.reason()).as("모르는 코드의 사유를 「확인 못 함」으로 치지 않는다").isNull();
		assertThat(blocking.code()).isEqualTo("SOMETHING_NEW");
	}
}
