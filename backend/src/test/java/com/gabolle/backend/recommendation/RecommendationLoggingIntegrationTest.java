package com.gabolle.backend.recommendation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gabolle.backend.event.domain.EventOutbox;
import com.gabolle.backend.event.domain.Producer;
import com.gabolle.backend.event.repository.EventOutboxRepository;
import com.gabolle.backend.recommendation.adapter.EngineCandidate;
import com.gabolle.backend.recommendation.adapter.EngineCandidateBatch;
import com.gabolle.backend.recommendation.adapter.EngineLatencies;
import com.gabolle.backend.recommendation.adapter.EngineVersions;
import com.gabolle.backend.recommendation.application.RecommendationCodes;
import com.gabolle.backend.recommendation.application.RecommendationCommand;
import com.gabolle.backend.recommendation.application.RecommendationFailedException;
import com.gabolle.backend.recommendation.application.RecommendationResult;
import com.gabolle.backend.recommendation.application.RecommendationService;
import com.gabolle.backend.recommendation.application.RecommendedPlace;
import com.gabolle.backend.recommendation.domain.CandidateStage;
import com.gabolle.backend.recommendation.domain.ConstraintSeverity;
import com.gabolle.backend.recommendation.domain.ConstraintVerdict;
import com.gabolle.backend.recommendation.domain.FallbackMode;
import com.gabolle.backend.recommendation.domain.JobStatus;
import com.gabolle.backend.recommendation.domain.JobType;
import com.gabolle.backend.recommendation.domain.RecommendationCandidate;
import com.gabolle.backend.recommendation.domain.RecommendationJob;
import com.gabolle.backend.recommendation.repository.RecommendationCandidateRepository;
import com.gabolle.backend.recommendation.repository.RecommendationJobRepository;
import com.gabolle.backend.recommendation.support.FakeRecommendationEngine;
import com.gabolle.backend.recommendation.support.PersonalizationFixture;
import com.gabolle.backend.recommendation.support.PostgresIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 추천 요청 한 건이 만든 것 전부가 request_id 하나로 되찾아지는가. */
class RecommendationLoggingIntegrationTest extends PostgresIntegrationTest {

	@Autowired
	private RecommendationService recommendationService;

	@Autowired
	private FakeRecommendationEngine engine;

	@Autowired
	private RecommendationJobRepository jobRepository;

	@Autowired
	private RecommendationCandidateRepository candidateRepository;

	@Autowired
	private EventOutboxRepository outboxRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private PersonalizationFixture.Ids references;

	@BeforeEach
	void clean() {
		this.candidateRepository.deleteAllInBatch();
		this.jobRepository.deleteAllInBatch();
		this.outboxRepository.deleteAllInBatch();
		this.engine.reset();
		// 외래키가 붙어 있어 요청이 가리키는 사용자·여행·스냅샷은 실제 행이어야 한다.
		this.references = PersonalizationFixture.insert(this.jdbcTemplate);
	}

	@Test
	@DisplayName("전체 후보 10개에 반환 5개면 Candidate 는 10행이고, PASS·FAIL·UNKNOWN 이 모두 남는다")
	void storesEveryCandidateNotOnlyTheReturnedOnes() {
		List<EngineCandidate> candidates = new ArrayList<>();
		for (int i = 0; i < 6; i++) {
			candidates.add(FakeRecommendationEngine.passing(UUID.randomUUID(), 0.9 - (i * 0.05)));
		}
		for (int i = 0; i < 2; i++) {
			candidates.add(FakeRecommendationEngine.failing(UUID.randomUUID(), "ALLERGY_PEANUT"));
		}
		for (int i = 0; i < 2; i++) {
			candidates.add(FakeRecommendationEngine.unknownWith(UUID.randomUUID(), "SHADE_RATIO",
					ConstraintSeverity.PREFERRED));
		}
		this.engine.willReturn(FakeRecommendationEngine.batchOf(candidates));

		RecommendationResult result = this.recommendationService.recommend(command(5));

		assertThat(result.generatedCandidateCount()).isEqualTo(10);
		assertThat(result.returnedCandidateCount()).isEqualTo(5);

		List<RecommendationCandidate> stored = this.candidateRepository
				.findByRequestIdOrderByFinalRankAscPlaceIdAsc(result.requestId());
		assertThat(stored).hasSize(10);
		assertThat(stored).extracting(RecommendationCandidate::getConstraintVerdict)
				.contains(ConstraintVerdict.PASS, ConstraintVerdict.FAIL, ConstraintVerdict.UNKNOWN);
	}

	@Test
	@DisplayName("🔴 FAIL 후보는 노출되지도, 랭킹 대상이 되지도 않는다")
	void failingCandidatesAreNeverReturned() {
		UUID banned = UUID.randomUUID();
		this.engine.willReturn(FakeRecommendationEngine.batchOf(List.of(
				FakeRecommendationEngine.passing(UUID.randomUUID(), 0.4),
				FakeRecommendationEngine.failing(banned, "ALLERGY_PEANUT"))));

		RecommendationResult result = this.recommendationService.recommend(command(5));

		assertThat(result.items()).extracting(RecommendedPlace::placeId).doesNotContain(banned);

		RecommendationCandidate stored = findByPlace(result.requestId(), banned);
		assertThat(stored.isReturned()).isFalse();
		assertThat(stored.isEligible()).isFalse();
		assertThat(stored.getCandidateStage()).isEqualTo(CandidateStage.HARD_FILTERED);
		assertThat(stored.getFinalRank()).isNull();
	}

	@Test
	@DisplayName("소프트 조건의 UNKNOWN 은 제외가 아니라 경고로 나오고, 판정은 UNKNOWN 그대로 저장된다")
	void softUnknownIsWarnedNotExcluded() {
		UUID uncertain = UUID.randomUUID();
		this.engine.willReturn(FakeRecommendationEngine.batchOf(List.of(
				FakeRecommendationEngine.unknownWith(uncertain, "SHADE_RATIO", ConstraintSeverity.PREFERRED))));

		RecommendationResult result = this.recommendationService.recommend(command(5));

		// 기본 정책(FR-REC-02)에서는 결과에 나온다 — 다만 경고를 달고서.
		assertThat(result.items()).extracting(RecommendedPlace::placeId).contains(uncertain);
		assertThat(result.items().get(0).warningCodes())
				.contains(RecommendationCodes.WARNING_CONSTRAINT_UNKNOWN);

		RecommendationCandidate stored = findByPlace(result.requestId(), uncertain);
		assertThat(stored.getConstraintVerdict()).isEqualTo(ConstraintVerdict.UNKNOWN);
		assertThat(stored.getWarningCodes()).contains(RecommendationCodes.WARNING_CONSTRAINT_UNKNOWN);
		assertThat(stored.getUnknownFacts()).contains("SHADE_RATIO");
	}

	@Test
	@DisplayName("🔴 REQUIRED 등급이 미확인인 후보는 결과에 나오지 않는다 — 행과 사유는 남는다")
	void unknownRequiredConstraintIsNeverReturned() {
		UUID risky = UUID.randomUUID();
		this.engine.willReturn(FakeRecommendationEngine.batchOf(List.of(
				FakeRecommendationEngine.passing(UUID.randomUUID(), 0.9),
				FakeRecommendationEngine.unknownWith(risky, "ALLERGY_PEANUT", ConstraintSeverity.REQUIRED))));

		RecommendationResult result = this.recommendationService.recommend(command(5));

		assertThat(result.items()).extracting(RecommendedPlace::placeId).doesNotContain(risky);

		RecommendationCandidate stored = findByPlace(result.requestId(), risky);
		assertThat(stored.isReturned()).isFalse();
		assertThat(stored.isEligible()).isFalse();
		assertThat(stored.getConstraintVerdict()).isEqualTo(ConstraintVerdict.UNKNOWN);
		assertThat(stored.getWarningCodes())
				.contains(RecommendationCodes.WARNING_UNKNOWN_CONSTRAINT_EXCLUDED);
	}

	@Test
	@DisplayName("🔴 점수가 없는 후보는 순위 없이 저장되고 랭킹 수에도 안 들어간다")
	void scorelessCandidatesGetNoRank() {
		UUID scoreless = UUID.randomUUID();
		this.engine.willReturn(FakeRecommendationEngine.batchOf(List.of(
				FakeRecommendationEngine.passing(UUID.randomUUID(), 0.9),
				FakeRecommendationEngine.scoreless(scoreless))));

		RecommendationResult result = this.recommendationService.recommend(command(5));

		assertThat(result.generatedCandidateCount()).isEqualTo(2);
		assertThat(result.eligibleCandidateCount()).isEqualTo(1);

		RecommendationCandidate stored = findByPlace(result.requestId(), scoreless);
		assertThat(stored.getFinalRank()).isNull();
		assertThat(stored.getOriginalRank()).isNull();
		assertThat(stored.isEligible()).isFalse();
		assertThat(stored.getWarningCodes()).contains(RecommendationCodes.WARNING_SCORE_MISSING);
	}

	@Test
	@DisplayName("Job 의 단계별 후보 수와 실제 Candidate 행 수가 일치한다")
	void jobCountsMatchStoredRows() {
		this.engine.willReturn(FakeRecommendationEngine.batchOf(List.of(
				FakeRecommendationEngine.passing(UUID.randomUUID(), 0.9),
				FakeRecommendationEngine.passing(UUID.randomUUID(), 0.8),
				FakeRecommendationEngine.failing(UUID.randomUUID(), "PET_NOT_ALLOWED"))));

		RecommendationResult result = this.recommendationService.recommend(command(1));

		RecommendationJob job = this.jobRepository.findByRequestId(result.requestId()).orElseThrow();
		assertThat(job.getGeneratedCandidateCount()).isEqualTo(3);
		assertThat(job.getEligibleCandidateCount()).isEqualTo(2);
		assertThat(job.getReturnedCandidateCount()).isEqualTo(1);
		assertThat(this.candidateRepository.countByRequestId(result.requestId()))
				.isEqualTo(job.getGeneratedCandidateCount());
	}

	@Test
	@DisplayName("한 request_id 로 Job 과 모든 후보를 되찾을 수 있고, 응답에도 그 키가 들어 있다")
	void everythingIsReachableFromOneRequestId() {
		this.engine.willReturn(FakeRecommendationEngine.batchOf(List.of(
				FakeRecommendationEngine.passing(UUID.randomUUID(), 0.9),
				FakeRecommendationEngine.failing(UUID.randomUUID(), "CLOSED"))));

		RecommendationResult result = this.recommendationService.recommend(command(5));

		assertThat(result.requestId()).isNotNull();
		// request_id 는 서버가 만들어 엔진까지 그대로 넘어간다. 클라이언트가 정하지 않는다.
		assertThat(this.engine.lastRequest().requestId()).isEqualTo(result.requestId());

		assertThat(this.jobRepository.findByRequestId(result.requestId())).isPresent();
		assertThat(this.candidateRepository.findByRequestIdOrderByFinalRankAscPlaceIdAsc(result.requestId()))
				.hasSize(2);

		List<EventOutbox> events = this.outboxRepository
				.findByEventTypeOrderByOccurredAtAsc(RecommendationCodes.EVENT_RECOMMENDATION_REQUESTED);
		assertThat(events).hasSize(1);
		assertThat(events.get(0).getAggregateId()).isEqualTo(result.requestId());
		// request_id 는 payload 에 중복 저장하지 않는다. 추천 축에서는 aggregate_id 가
		// 정본이고 나머지 envelope 값은 전용 컬럼으로 간다 — 같은 값을 둘에 두면 나중에
		// 서로 달라졌을 때 어느 쪽이 맞는지 알 수 없다.
		assertThat(events.get(0).getPayload()).doesNotContain("\"request_id\"");
		assertThat(events.get(0).getUserId()).isEqualTo(this.references.userId());
		assertThat(events.get(0).getTripId()).isEqualTo(this.references.tripId());
		assertThat(events.get(0).getProducer()).isEqualTo(Producer.SERVER);
		// GB-API-001 4.3 이 요구하는 버전 넷이 이벤트 본문에도 실린다.
		assertThat(events.get(0).getPayload())
				.contains("model_version", "feature_version", "ontology_version", "policy_version");

		// occurred_at 은 요청 시각이다. 완료 시각을 넣으면 지연이 긴 요청일수록 발생 시각이
		// 뒤로 밀려 시간대별 요청량 분석이 어긋난다.
		RecommendationJob job = this.jobRepository.findByRequestId(result.requestId()).orElseThrow();
		assertThat(events.get(0).getOccurredAt()).isEqualTo(job.getCreatedAt());
		assertThat(events.get(0).getOccurredAt()).isBeforeOrEqualTo(job.getCompletedAt());
	}

	@Test
	@DisplayName("🔴 버전을 못 구하고 죽은 요청도 recommendation_failed 로 남는다")
	void failuresBeforeVersionsAreResolvedStillEmitAFailedEvent() {
		this.engine.willFailWith("ENGINE_TIMEOUT", true);

		assertThatThrownBy(() -> this.recommendationService.recommend(command(5)))
				.isInstanceOf(RecommendationFailedException.class);

		// 버전이 없으니 recommendation_requested 는 만들 수 없다 (P0 8.1 이 버전을 요구한다).
		assertThat(failedEvents()).hasSize(1);
		assertThat(requestedEvents()).isEmpty();

		EventOutbox failed = failedEvents().get(0);
		// PostgreSQL 이 jsonb 를 정규화하며 공백을 넣으므로 키 이름만 본다. 값은 Job 으로 확인한다.
		assertThat(failed.getPayload()).contains("ENGINE_TIMEOUT", "timeout_occurred", "failure_stage");
		// 요청 시각은 payload 에 따로 실린다 — occurred_at(실패 시각)과 빼면 버틴 시간이 나온다.
		assertThat(failed.getPayload()).contains("requested_at");

		RecommendationJob job = this.jobRepository.findAll().get(0);
		assertThat(job.isTimeoutOccurred()).isTrue();
		// 버전 칸은 비어 있다. 그게 이 이벤트가 존재하는 이유다.
		assertThat(job.getModelVersion()).isNull();
	}

	@Test
	@DisplayName("🔴 버전 확보 뒤의 실패는 requested 와 failed 를 둘 다 남긴다")
	void failuresAfterVersionsAreResolvedEmitBothEvents() {
		EngineCandidate leaky = new EngineCandidate(UUID.randomUUID(), "ONTOLOGY_SEED", ConstraintVerdict.PASS,
				List.of(), List.of(), 1.0,
				Map.of("origin", Map.of("lat", 35.1796, "lng", 129.0756)),
				Map.of(), 0.9, List.of(), List.of());
		this.engine.willReturn(FakeRecommendationEngine.batchOf(List.of(leaky)));

		assertThatThrownBy(() -> this.recommendationService.recommend(command(5)))
				.isInstanceOf(RecommendationFailedException.class);

		RecommendationJob job = this.jobRepository.findAll().get(0);
		assertThat(job.getJobStatus()).isEqualTo(JobStatus.FAILED);

		// 둘은 서로를 대체하지 않는다 — "이 버전으로 요청이 있었다" 와 "그 요청이 이렇게 끝났다".
		assertThat(requestedEvents()).hasSize(1);
		assertThat(failedEvents()).hasSize(1);
		assertThat(requestedEvents().get(0).getAggregateId()).isEqualTo(job.getRequestId());
		assertThat(failedEvents().get(0).getAggregateId()).isEqualTo(job.getRequestId());
		assertThat(failedEvents().get(0).getPayload())
				.contains(RecommendationCodes.ERROR_SENSITIVE_DATA_REJECTED);
	}

	@Test
	@DisplayName("성공한 요청에는 recommendation_failed 가 없다")
	void successfulRequestsEmitNoFailedEvent() {
		this.engine.willReturn(FakeRecommendationEngine.batchOf(List.of(
				FakeRecommendationEngine.passing(UUID.randomUUID(), 0.9))));

		this.recommendationService.recommend(command(5));

		assertThat(requestedEvents()).hasSize(1);
		assertThat(failedEvents()).isEmpty();
	}

	@Test
	@DisplayName("반환할 후보가 없는 요청도 두 이벤트를 남기고 후보 행은 그대로 있다")
	void noFeasibleResultEmitsBothEventsAndKeepsCandidates() {
		this.engine.willReturn(FakeRecommendationEngine.batchOf(List.of(
				FakeRecommendationEngine.failing(UUID.randomUUID(), "ALLERGY_PEANUT"))));

		assertThatThrownBy(() -> this.recommendationService.recommend(command(5)))
				.isInstanceOf(RecommendationFailedException.class);

		RecommendationJob job = this.jobRepository.findAll().get(0);
		assertThat(requestedEvents()).hasSize(1);
		assertThat(failedEvents()).hasSize(1);
		assertThat(failedEvents().get(0).getPayload())
				.contains(RecommendationCodes.ERROR_NO_FEASIBLE_RESULT);
		assertThat(this.candidateRepository.countByRequestId(job.getRequestId())).isEqualTo(1);
	}

	private List<EventOutbox> requestedEvents() {
		return this.outboxRepository
				.findByEventTypeOrderByOccurredAtAsc(RecommendationCodes.EVENT_RECOMMENDATION_REQUESTED);
	}

	private List<EventOutbox> failedEvents() {
		return this.outboxRepository
				.findByEventTypeOrderByOccurredAtAsc(RecommendationCodes.EVENT_RECOMMENDATION_FAILED);
	}

	@Test
	@DisplayName("Job 이 공개 JobDto 가 요구하는 칸을 채운다 — type · resource · status")
	void jobCarriesThePublicContractFields() {
		this.engine.willReturn(FakeRecommendationEngine.batchOf(List.of(
				FakeRecommendationEngine.passing(UUID.randomUUID(), 0.9))));

		RecommendationResult result = this.recommendationService.recommend(command(5));

		RecommendationJob job = this.jobRepository.findByRequestId(result.requestId()).orElseThrow();
		assertThat(job.getJobType()).isEqualTo(JobType.ITINERARY_GENERATION);
		assertThat(job.getResourceType()).isNotNull();
		assertThat(job.getResourceId()).isEqualTo(job.getTripId());
		assertThat(job.getJobStatus()).isEqualTo(JobStatus.SUCCEEDED);
		assertThat(job.getPolicyVersion()).isEqualTo("policy-2026-09-01");
	}

	@Test
	@DisplayName("🔴 후보가 0건이면 성공이 아니라 RECOMMENDATION_NO_FEASIBLE_RESULT 로 남는다")
	void anEmptyCandidateSetIsNotFeasibleNotSuccessful() {
		this.engine.willReturn(FakeRecommendationEngine.emptyBatch());

		// 오류 코드는 메시지가 아니라 예외의 필드에 있다 — 공개 계층이 그것을 그대로 422 로 옮긴다.
		assertThatThrownBy(() -> this.recommendationService.recommend(command(5)))
				.isInstanceOf(RecommendationFailedException.class)
				.extracting(t -> ((RecommendationFailedException) t).getErrorCode())
				.isEqualTo(RecommendationCodes.ERROR_NO_FEASIBLE_RESULT);

		RecommendationJob job = this.jobRepository.findAll().get(0);
		assertThat(job.getJobStatus()).isEqualTo(JobStatus.FAILED);
		assertThat(job.getErrorCode()).isEqualTo(RecommendationCodes.ERROR_NO_FEASIBLE_RESULT);
		assertThat(job.getGeneratedCandidateCount()).isZero();
	}

	@Test
	@DisplayName("🔴 후보를 다 만들었지만 전부 하드 제약에 걸리면 — 후보 행은 전부 남고 요청은 실패한다")
	void allCandidatesFilteredKeepsTheRowsAndFailsTheRequest() {
		this.engine.willReturn(FakeRecommendationEngine.batchOf(List.of(
				FakeRecommendationEngine.failing(UUID.randomUUID(), "ALLERGY_PEANUT"),
				FakeRecommendationEngine.failing(UUID.randomUUID(), "HALAL_REQUIRED"))));

		assertThatThrownBy(() -> this.recommendationService.recommend(command(5)))
				.isInstanceOf(RecommendationFailedException.class);

		RecommendationJob job = this.jobRepository.findAll().get(0);
		assertThat(job.getJobStatus()).isEqualTo(JobStatus.FAILED);
		assertThat(job.getErrorCode()).isEqualTo(RecommendationCodes.ERROR_NO_FEASIBLE_RESULT);
		assertThat(job.getGeneratedCandidateCount()).isEqualTo(2);
		// 왜 빈손이었는지는 이 두 행에만 적혀 있다. 지우면 영영 못 묻는다.
		assertThat(this.candidateRepository.countByRequestId(job.getRequestId())).isEqualTo(2);
		// 하드 제약을 슬쩍 풀어 결과를 채우지 않았다.
		assertThat(this.candidateRepository.findByRequestIdAndReturnedTrueOrderByFinalRankAsc(job.getRequestId()))
				.isEmpty();
	}

	@Test
	@DisplayName("fallback 결과에도 후보·순위·버전이 그대로 저장되고 상태는 SUCCEEDED 다")
	void fallbackResultsKeepCandidatesRanksAndVersions() {
		EngineCandidateBatch fallbackBatch = new EngineCandidateBatch(
				List.of(FakeRecommendationEngine.passing(UUID.randomUUID(), 0.6)),
				FakeRecommendationEngine.versions(),
				EngineLatencies.unmeasured(),
				FallbackMode.BASELINE,
				"MODEL_TIMEOUT");
		this.engine.willReturn(fallbackBatch);

		RecommendationResult result = this.recommendationService.recommend(command(5));

		// 대체 경로였다는 사실은 상태가 아니라 fallbackMode 가 나타낸다.
		assertThat(result.jobStatus()).isEqualTo(JobStatus.SUCCEEDED);
		assertThat(result.fallbackMode()).isEqualTo(FallbackMode.BASELINE);
		assertThat(result.fallbackReason()).isEqualTo("MODEL_TIMEOUT");
		assertThat(result.modelVersion()).isEqualTo("model-1.2.0");

		RecommendationCandidate stored = this.candidateRepository
				.findByRequestIdOrderByFinalRankAscPlaceIdAsc(result.requestId()).get(0);
		assertThat(stored.getFinalRank()).isEqualTo(1);
		assertThat(stored.getFallbackMode()).isEqualTo(FallbackMode.BASELINE);

		RecommendationJob job = this.jobRepository.findByRequestId(result.requestId()).orElseThrow();
		assertThat(job.getModelVersion()).isEqualTo("model-1.2.0");
		assertThat(job.getFeatureVersion()).isEqualTo("feature-3");
		assertThat(job.getOntologyVersion()).isEqualTo("ontology-2026-09-01");
		assertThat(job.getPolicyVersion()).isEqualTo("policy-2026-09-01");
		assertThat(job.getDatasetVersion()).isEqualTo("dataset-2026-08-31");
		assertThat(job.getServiceVersion()).isEqualTo("test-service-0.0.1");
		assertThat(job.getDeploymentEnvironment()).isEqualTo("test");
	}

	@Test
	@DisplayName("🔴 버전이 비어 있으면 조용히 통과하지 않는다 — Job 은 FAILED 로 남고 재시도 대상도 아니다")
	void missingVersionsFailLoudlyAndAreRecorded() {
		this.engine.willReturn(new EngineCandidateBatch(
				List.of(FakeRecommendationEngine.passing(UUID.randomUUID(), 0.9)),
				new EngineVersions("model-1.2.0", null, "ontology-1", "  ", "dataset-1"),
				EngineLatencies.unmeasured(), FallbackMode.RULE, null));

		assertThatThrownBy(() -> this.recommendationService.recommend(command(5)))
				.isInstanceOf(RecommendationFailedException.class)
				.hasMessageContaining("VERSION_UNRESOLVED");

		List<RecommendationJob> jobs = this.jobRepository.findAll();
		assertThat(jobs).hasSize(1);
		assertThat(jobs.get(0).getJobStatus()).isEqualTo(JobStatus.FAILED);
		assertThat(jobs.get(0).getErrorCode()).isEqualTo("VERSION_UNRESOLVED");
		// 다시 불러도 같은 버전이 비어 있을 테니 재시도를 권하지 않는다.
		assertThat(jobs.get(0).isRetryable()).isFalse();
		// 후보는 남지 않는다 — 성공한 요청과 헷갈리지 않게.
		assertThat(this.candidateRepository.count()).isZero();
		// 버전이 없으니 recommendation_requested 도 만들 수 없다. 대신 실패 이벤트는 남는다.
		assertThat(requestedEvents()).isEmpty();
		assertThat(failedEvents()).hasSize(1);
	}

	@Test
	@DisplayName("정밀 좌표가 섞인 피처는 저장되지 않고 실패로 남는다")
	void preciseCoordinatesInFeatureValuesAreRejected() {
		EngineCandidate leaky = new EngineCandidate(UUID.randomUUID(), "ONTOLOGY_SEED", ConstraintVerdict.PASS,
				List.of(), List.of(), 1.0,
				Map.of("origin", Map.of("lat", 35.1796, "lng", 129.0756)),
				Map.of(), 0.9, List.of(), List.of());
		this.engine.willReturn(FakeRecommendationEngine.batchOf(List.of(leaky)));

		assertThatThrownBy(() -> this.recommendationService.recommend(command(5)))
				.isInstanceOf(RecommendationFailedException.class);

		assertThat(this.candidateRepository.count()).isZero();
		assertThat(this.jobRepository.findAll()).singleElement()
				.extracting(RecommendationJob::getErrorCode)
				.isEqualTo(RecommendationCodes.ERROR_SENSITIVE_DATA_REJECTED);
	}

	private RecommendationCommand command(int topK) {
		return new RecommendationCommand(this.references.userId(), JobType.ITINERARY_GENERATION,
				this.references.tripId(), this.references.tripVersion(),
				this.references.preferenceSnapshotId(), this.references.constraintSnapshotId(),
				null, null, null, "app-1.0.0", topK, null);
	}

	private RecommendationCandidate findByPlace(UUID requestId, UUID placeId) {
		return this.candidateRepository.findByRequestIdOrderByFinalRankAscPlaceIdAsc(requestId).stream()
				.filter((candidate) -> candidate.getPlaceId().equals(placeId))
				.findFirst()
				.orElseThrow(() -> new AssertionError("후보가 저장되지 않았다: " + placeId));
	}
}
