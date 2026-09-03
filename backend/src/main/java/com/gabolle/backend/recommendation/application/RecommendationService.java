package com.gabolle.backend.recommendation.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.gabolle.backend.common.privacy.SensitiveDataInPayloadException;
import com.gabolle.backend.event.application.OutboxAppendCommand;
import com.gabolle.backend.event.domain.Producer;
import com.gabolle.backend.recommendation.adapter.EngineCandidateBatch;
import com.gabolle.backend.recommendation.adapter.EngineRequest;
import com.gabolle.backend.recommendation.adapter.EngineVersions;
import com.gabolle.backend.recommendation.adapter.RecommendationEngineException;
import com.gabolle.backend.recommendation.adapter.RecommendationEnginePort;
import com.gabolle.backend.recommendation.adapter.RecommendationVersionsMissingException;
import com.gabolle.backend.recommendation.config.RecommendationProperties;
import com.gabolle.backend.recommendation.domain.JobStage;
import com.gabolle.backend.recommendation.domain.RecommendationJob;

/**
 * 추천 요청 한 건을 처음부터 끝까지 끌고 간다.
 *
 * <pre>
 * 요청 → request_id · job_id 생성 → 입력 스냅샷·버전 연결 → 후보 전체 생성
 *      → 모든 후보의 제약 판정 저장 → 랭킹 가능한 것만 순위 → 반환 여부 저장
 *      → recommendation_requested 를 같은 트랜잭션의 Outbox 에 저장
 *      → request_id 가 포함된 결과 반환
 * </pre>
 *
 * <p>🔴 이 클래스에는 {@code @Transactional} 이 없다. 엔진 호출은 바깥 네트워크를 타므로
 * 트랜잭션 안에 넣으면 DB 커넥션을 그동안 붙잡고 있게 된다. 저장만
 * {@link RecommendationRecorder} 에서 한 트랜잭션으로 묶는다.
 *
 * <p>🔴 이 메서드는 <b>동기</b>다. GB-API-001 API-10 은 "지금 갈 곳"(REC-04)만 동기로,
 * 전체 일정 생성(REC-01)은 202 + jobId 비동기로 정한다. 비동기 Job 러너는
 * S15P21E201-543 범위가 아니므로, 여기서 만든 Job 행이 그 러너가 이어받을 자리가 된다.
 *
 * <h2>공개 경로 — 정해졌다 (2026-09-02)</h2>
 *
 * 두 문서가 서로 다르게 적고 있었다. <b>API 명세서(GB-API-001)를 따른다.</b>
 *
 * <ul>
 * <li>REC-01 {@code POST /api/v1/trips/{tripId}/recommendation-jobs} → 202 JobDto</li>
 * <li>JOB-01 {@code GET /api/v1/jobs/{jobId}} → JobDto</li>
 * <li>REC-04 {@code POST /api/v1/recommendations/now} → 200 RecommendationResponse</li>
 * </ul>
 *
 * 기능·화면 상세설계서의 {@code POST /api/v1/recommendation-jobs}(tripId 없음)와
 * {@code GET /api/v1/recommendation-jobs/{id}} 는 <b>쓰지 않는다.</b> 컨트롤러를 만드는
 * 사람은 위 경로로 만든다.
 *
 * <p>🔴 컨트롤러는 아직 없다. 만들 때 함께 지켜야 하는 것: {@code data/error/meta.requestId}
 * envelope(API-01), 내부 점수 제거(API-04), {@code Idempotency-Key}(API-09), 그리고
 * {@code tripId} 소유·참여 관계 검증(FR-SEC-01).
 */
@Service
@Profile({ "db", "dev" })
public class RecommendationService {

	/**
	 * 🔴 {@code ObjectProvider} 로 받는 이유가 있다. 이 배포에 추천 엔진 구현이 없을 수 있고,
	 * 그때 <b>애플리케이션 전체가 못 뜨면 안 된다</b> — 다른 도메인이 DB 를 켜려다 추천 때문에
	 * 막히는 일이 실제로 생겼다.
	 *
	 * <p>{@code @ConditionalOnBean} 을 쓰지 않은 것도 의도다. 사용자 설정에서는 평가 순서가
	 * 보장되지 않아, 나중에 진짜 어댑터가 붙었는데 <b>조용히 무시되는</b> 실패가 난다.
	 * 여기서는 부를 때 확인하므로 순서에 기대지 않는다.
	 */
	private final ObjectProvider<RecommendationEnginePort> enginePort;

	private final CandidateAssembler candidateAssembler;

	private final RecommendationRecorder recorder;

	private final RecommendationProperties properties;

	private final Clock clock;

	public RecommendationService(ObjectProvider<RecommendationEnginePort> enginePort,
			CandidateAssembler candidateAssembler, RecommendationRecorder recorder,
			RecommendationProperties properties, Clock clock) {
		this.enginePort = enginePort;
		this.candidateAssembler = candidateAssembler;
		this.recorder = recorder;
		this.properties = properties;
		this.clock = clock;
	}

	/**
	 * @throws RecommendationFailedException 결과를 만들지 못했을 때. 던지기 전에 Job 은
	 *     FAILED 로 이미 저장돼 있고, 후보를 만들었다면 그 후보들도 함께 저장돼 있다
	 */
	public RecommendationResult recommend(RecommendationCommand command) {
		// 🔴 서버가 만든다. 클라이언트가 준 값을 쓰지 않는다.
		UUID requestId = UUID.randomUUID();
		UUID jobId = UUID.randomUUID();
		OffsetDateTime createdAt = OffsetDateTime.now(this.clock);
		long startedNanos = System.nanoTime();

		RecommendationJob job = RecommendationJob.start(jobId, requestId, command.userId(), command.jobType(),
				createdAt);
		job.applyRequestContext(command.tripId(), command.tripVersion(), command.preferenceSnapshotId(),
				command.constraintSnapshotId(), command.itineraryId(), command.itineraryVersion(),
				command.baseVersion(), command.appVersion());

		int topK = (command.topK() == null) ? this.properties.defaultTopK() : command.topK();

		RecommendationEnginePort engine = this.enginePort.getIfAvailable();
		if (engine == null) {
			// 기동은 됐지만 이 배포에는 엔진이 없다. 조용히 빈 결과를 주지 않는다.
			throw abandon(job, RecommendationCodes.ERROR_ENGINE_NOT_CONFIGURED, JobStage.CANDIDATE_GENERATION,
					false, false, createdAt, startedNanos, null);
		}

		EngineCandidateBatch batch;
		try {
			batch = engine.generate(new EngineRequest(requestId, command.userId(), command.tripId(),
					command.tripVersion(), command.preferenceSnapshotId(), command.constraintSnapshotId(),
					command.itineraryId(), command.itineraryVersion(), topK));
		}
		catch (RecommendationEngineException ex) {
			// 엔진이 잠깐 죽었거나 느렸을 수 있다 — 다시 부르면 달라질 여지가 있다.
			throw abandon(job, ex.getErrorCode(), JobStage.CANDIDATE_GENERATION, ex.isTimeout(), true,
					createdAt, startedNanos, ex);
		}
		catch (RuntimeException ex) {
			throw abandon(job, RecommendationCodes.ERROR_ENGINE_UNAVAILABLE, JobStage.CANDIDATE_GENERATION,
					false, true, createdAt, startedNanos, ex);
		}

		// 후보가 만들어진 시각. 저장이 끝난 시각(completed_at)과 나누는 이유는, 저장이 느렸던
		// 요청과 추천 계산이 느렸던 요청을 나중에 구분할 수 있어야 하기 때문이다.
		OffsetDateTime generatedAt = OffsetDateTime.now(this.clock);

		List<String> missingVersions = resolveMissingVersions(batch.versions());
		if (!missingVersions.isEmpty()) {
			// 🔴 여기서 기본값을 넣지 않는다. 재현할 수 없는 결과를 재현 가능한 것처럼 남기는 것이
			//    결과를 아예 안 남기는 것보다 나쁘다. 그리고 다시 불러도 같은 버전이 비어 있을
			//    테니 retryable=false 다.
			throw abandon(job, RecommendationVersionsMissingException.ERROR_CODE, JobStage.VERSION_RESOLUTION,
					false, false, createdAt, startedNanos,
					new RecommendationVersionsMissingException(missingVersions));
		}

		job.applyVersions(batch.versions().modelVersion(), batch.versions().featureVersion(),
				batch.versions().ontologyVersion(), batch.versions().policyVersion(),
				batch.versions().datasetVersion(), this.properties.serviceVersion(),
				this.properties.deploymentEnvironment());

		CandidateAssembly assembly;
		try {
			assembly = this.candidateAssembler.assemble(requestId, batch, topK,
					this.properties.unknownExclusionThreshold(), this.properties.unspecifiedSeverity(),
					createdAt);
		}
		catch (SensitiveDataInPayloadException ex) {
			// 🔴 저장하지 않고 실패로 남긴다. 이 요청이 있었다는 사실과 왜 버렸는지는 남아야
			//    하고, 문제가 된 값 자체는 남으면 안 된다 — 예외 메시지는 경로만 담는다.
			throw abandon(job, RecommendationCodes.ERROR_SENSITIVE_DATA_REJECTED, JobStage.RANKING, false,
					false, createdAt, startedNanos, ex);
		}
		catch (RuntimeException ex) {
			throw abandon(job, RecommendationCodes.ERROR_CANDIDATE_ASSEMBLY_FAILED, JobStage.RANKING, false,
					false, createdAt, startedNanos, ex);
		}

		job.applyCounts(assembly.generatedCount(), assembly.eligibleCount(), assembly.returnedCount());
		job.applyLatencies(elapsedMs(startedNanos), batch.latencies().candidateGenerationMs(),
				batch.latencies().ontologyMs(), batch.latencies().featureLookupMs(),
				batch.latencies().rankingMs(), batch.latencies().optimizationMs());

		OffsetDateTime completedAt = OffsetDateTime.now(this.clock);
		// 🔴 occurred_at 은 <b>요청 시각</b>이지 완료 시각이 아니다. 이 이벤트의 이름이
		//    recommendation_requested 다 — 요청이 일어난 순간이 발생 시각이다.
		OutboxAppendCommand event = buildRequestedEvent(job, createdAt);

		if (assembly.returnedCount() == 0) {
			// 여기부터는 실패지만, 후보는 이미 다 만들어졌다.
			// 🔴 반환할 것이 없으면 성공이 아니다 — GB-API-001 5장이 422
			//    RECOMMENDATION_NO_FEASIBLE_RESULT 로 정한다. 하드 제약을 슬쩍 풀어 목록을
			//    채우지 않는다.
			//
			//    그래도 후보 행은 <b>전부</b> 저장한다. "왜 빈손이었나" 는 그 행들에만 적혀 있다.
			JobStage stage = (assembly.generatedCount() == 0)
					? JobStage.CANDIDATE_GENERATION
					: JobStage.CONSTRAINT_EVALUATION;
			job.markFailed(RecommendationCodes.ERROR_NO_FEASIBLE_RESULT, stage, completedAt, false, false);
			this.recorder.record(job, assembly.candidates(),
					List.of(event, buildFailedEvent(job, completedAt)));
			throw new RecommendationFailedException(requestId, jobId, RecommendationCodes.ERROR_NO_FEASIBLE_RESULT,
					stage, "반환할 수 있는 후보가 없다 (생성 " + assembly.generatedCount() + "건)", null);
		}

		job.markCompleted(generatedAt, completedAt, batch.fallbackMode(), batch.fallbackReason());
		this.recorder.record(job, assembly.candidates(), List.of(event));

		return new RecommendationResult(requestId, jobId, job.getJobType(), job.getJobStatus(),
				job.getGeneratedAt(), assembly.returnedItems(), assembly.generatedCount(),
				assembly.eligibleCount(), assembly.returnedCount(), job.getFallbackMode(),
				job.getFallbackReason(), job.getModelVersion(), job.getFeatureVersion(),
				job.getOntologyVersion(), job.getPolicyVersion(), job.getDatasetVersion(),
				job.getServiceVersion(), job.getDeploymentEnvironment());
	}

	private List<String> resolveMissingVersions(EngineVersions versions) {
		List<String> missing = new ArrayList<>(versions.missingFieldNames());
		if (isBlank(this.properties.serviceVersion())) {
			missing.add("service_version");
		}
		if (isBlank(this.properties.deploymentEnvironment())) {
			missing.add("deployment_environment");
		}
		return missing;
	}

	/**
	 * 후보를 하나도 못 만든 채 끝난다. Job 만 따로 저장하고, 부르는 쪽이 그대로 던질 예외를
	 * 만들어 돌려준다.
	 *
	 * <p>{@link RecommendationRecorder#recordFailure} 가 별도 트랜잭션이라 성공 트랜잭션이
	 * 깨진 뒤에도 이 기록만은 남는다.
	 *
	 * <p>🔴 <b>모든 실패는 {@code recommendation_failed} 이벤트를 남긴다.</b> 버전을 못 구했어도
	 * 남는다 — 그 이벤트는 버전을 요구하지 않는다. 이전에는 그런 실패가 이벤트 스트림에서
	 * 통째로 사라져서, 실패율을 재면 서비스가 제일 안 좋았던 순간이 통계에서 빠졌다.
	 *
	 * <p>버전까지 확보된 뒤의 실패라면 {@code recommendation_requested} 도 함께 남긴다.
	 * 두 이벤트는 서로를 대체하지 않는다 — 하나는 "이 버전으로 요청이 있었다", 다른 하나는
	 * "그 요청이 이렇게 끝났다" 다. P0 데이터 명세 2.3 의 "원자 이벤트를 보존한다" 가 이것이다.
	 */
	private RecommendationFailedException abandon(RecommendationJob job, String errorCode, JobStage stage,
			boolean timeout, boolean retryable, OffsetDateTime occurredAt, long startedNanos, Throwable cause) {
		OffsetDateTime failedAt = OffsetDateTime.now(this.clock);
		job.markFailed(errorCode, stage, failedAt, timeout, retryable);
		job.applyLatencies(elapsedMs(startedNanos), null, null, null, null, null);

		List<OutboxAppendCommand> events = new ArrayList<>();
		if (job.getModelVersion() != null) {
			events.add(buildRequestedEvent(job, occurredAt));
		}
		events.add(buildFailedEvent(job, failedAt));
		this.recorder.recordFailure(job, events);

		return new RecommendationFailedException(job.getRequestId(), job.getJobId(), errorCode, stage,
				"추천을 만들지 못했다: " + errorCode, cause);
	}

	/**
	 * {@code recommendation_requested} envelope.
	 *
	 * <p>🔴 개인정보는 여기 들어가지 않는다. 실명·이메일·전화번호·정밀 좌표 대신 스냅샷 ID 와
	 * 익명 UUID 만 싣는다. 값이 없는 필드는 키를 지우지 않고 {@code null} 로 남긴다 —
	 * "안 보냈다" 와 "없었다" 는 다른 사실이다.
	 */
	private OutboxAppendCommand buildRequestedEvent(RecommendationJob job, OffsetDateTime occurredAt) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("job_id", job.getJobId());
		payload.put("job_type", job.getJobType());
		payload.put("trip_version", job.getTripVersion());
		payload.put("itinerary_id", job.getItineraryId());
		payload.put("preference_snapshot_id", job.getPreferenceSnapshotId());
		payload.put("constraint_snapshot_id", job.getConstraintSnapshotId());
		payload.put("model_version", job.getModelVersion());
		payload.put("feature_version", job.getFeatureVersion());
		payload.put("ontology_version", job.getOntologyVersion());
		payload.put("policy_version", job.getPolicyVersion());
		payload.put("dataset_version", job.getDatasetVersion());
		payload.put("service_version", job.getServiceVersion());
		payload.put("app_version", job.getAppVersion());
		payload.put("deployment_environment", job.getDeploymentEnvironment());
		payload.put("generated_candidate_count", job.getGeneratedCandidateCount());
		payload.put("eligible_candidate_count", job.getEligibleCandidateCount());
		payload.put("returned_candidate_count", job.getReturnedCandidateCount());

		return new OutboxAppendCommand(
				UUID.randomUUID(),
				RecommendationCodes.EVENT_RECOMMENDATION_REQUESTED,
				this.properties.eventVersion(),
				RecommendationCodes.AGGREGATE_TYPE,
				job.getRequestId(),
				job.getUserId().toString(),
				payload,
				occurredAt,
				null,
				job.getUserId(),
				job.getTripId(),
				Producer.SERVER);
	}

	/**
	 * {@code recommendation_failed} envelope.
	 *
	 * <p>🔴 버전을 <b>요구하지 않는다</b>. 있으면 싣고 없으면 {@code null} 로 둔다 — 이 이벤트의
	 * 존재 이유가 바로 "버전을 못 구한 실패도 남기는 것" 이다.
	 *
	 * <p>🔴 {@code occurred_at} 은 <b>실패한 시각</b>이다. 요청 시각은 payload 의
	 * {@code requested_at} 에 따로 싣는다 — 둘을 빼면 "얼마나 버티다 죽었는가" 가 나온다.
	 */
	private OutboxAppendCommand buildFailedEvent(RecommendationJob job, OffsetDateTime occurredAt) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("job_id", job.getJobId());
		payload.put("job_type", job.getJobType());
		payload.put("itinerary_id", job.getItineraryId());
		payload.put("requested_at", job.getCreatedAt());
		payload.put("error_code", job.getErrorCode());
		payload.put("failure_stage", job.getFailureStage());
		payload.put("timeout_occurred", job.isTimeoutOccurred());
		payload.put("retryable", job.isRetryable());
		payload.put("retry_count", job.getRetryCount());
		payload.put("total_latency_ms", job.getTotalLatencyMs());
		payload.put("generated_candidate_count", job.getGeneratedCandidateCount());
		payload.put("eligible_candidate_count", job.getEligibleCandidateCount());
		// 아래 버전들은 못 구했을 수 있다. 그때 null 인 것이 이 이벤트의 요점이다.
		payload.put("model_version", job.getModelVersion());
		payload.put("feature_version", job.getFeatureVersion());
		payload.put("ontology_version", job.getOntologyVersion());
		payload.put("policy_version", job.getPolicyVersion());
		payload.put("dataset_version", job.getDatasetVersion());
		payload.put("service_version", job.getServiceVersion());
		payload.put("app_version", job.getAppVersion());
		payload.put("deployment_environment", job.getDeploymentEnvironment());

		return new OutboxAppendCommand(
				UUID.randomUUID(),
				RecommendationCodes.EVENT_RECOMMENDATION_FAILED,
				this.properties.eventVersion(),
				RecommendationCodes.AGGREGATE_TYPE,
				job.getRequestId(),
				job.getUserId().toString(),
				payload,
				occurredAt,
				null,
				job.getUserId(),
				job.getTripId(),
				Producer.SERVER);
	}

	private long elapsedMs(long startedNanos) {
		return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
