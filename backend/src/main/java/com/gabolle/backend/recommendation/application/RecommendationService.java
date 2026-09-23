package com.gabolle.backend.recommendation.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.gabolle.backend.common.privacy.SensitiveDataInPayloadException;
import com.gabolle.backend.event.application.OutboxAppendCommand;
import com.gabolle.backend.event.domain.EventType;
import com.gabolle.backend.event.domain.Producer;
import com.gabolle.backend.recommendation.adapter.EditorialPickBaseline;
import com.gabolle.backend.recommendation.adapter.EditorialPickBaselineProvider;
import com.gabolle.backend.recommendation.adapter.EngineCandidateBatch;
import com.gabolle.backend.recommendation.adapter.EngineRequest;
import com.gabolle.backend.recommendation.adapter.EngineVersions;
import com.gabolle.backend.recommendation.adapter.RecommendationEngineException;
import com.gabolle.backend.recommendation.adapter.RecommendationEnginePort;
import com.gabolle.backend.recommendation.adapter.RecommendationVersionsMissingException;
import com.gabolle.backend.recommendation.application.port.ItineraryDraft;
import com.gabolle.backend.recommendation.application.port.ItineraryDraftCommand;
import com.gabolle.backend.recommendation.application.port.ItineraryDraftPort;
import com.gabolle.backend.recommendation.application.port.ItineraryRevisionCommand;
import com.gabolle.backend.recommendation.application.port.ItineraryRevisionDraft;
import com.gabolle.backend.recommendation.config.RecommendationProperties;
import com.gabolle.backend.recommendation.domain.FallbackMode;
import com.gabolle.backend.recommendation.domain.JobStage;
import com.gabolle.backend.recommendation.domain.JobType;
import com.gabolle.backend.recommendation.domain.RecommendationJob;

/**
 * 추천 요청 한 건을 처음부터 끝까지 끌고 간다.
 *
 * <p>이 클래스에는 {@code @Transactional} 이 없다. 엔진 호출이 바깥 네트워크를 타므로
 * 트랜잭션 안에 넣으면 DB 커넥션을 그동안 붙잡고 있게 된다. 저장만
 * {@link RecommendationRecorder} 에서 한 트랜잭션으로 묶는다.
 *
 * <p>여기 경로는 동기다. 전체 일정 생성은 202 + jobId 비동기이고, 여기서 만든 Job 행이
 * 그 러너가 이어받을 자리가 된다.
 */
@Service
@Profile({ "db", "dev" })
public class RecommendationService {

	/**
	 * 이 배포에 추천 엔진 구현이 없어도 애플리케이션은 떠야 하므로 {@code ObjectProvider} 다.
	 * {@code @ConditionalOnBean} 은 평가 순서가 보장되지 않아 나중에 붙은 어댑터가 조용히
	 * 무시될 수 있어 쓰지 않는다.
	 */
	private final ObjectProvider<RecommendationEnginePort> enginePort;

	private final CandidateAssembler candidateAssembler;

	private final RecommendationRecorder recorder;

	private final RecommendationProperties properties;

	private final Clock clock;

	/** 추천만 스캔하는 시험 컨텍스트에는 이 포트가 없으므로 {@code ObjectProvider} 다. */
	private final ObjectProvider<ItineraryDraftPort> itineraryDraftPort;

	/**
	 * Editor's Pick 기준선. 이 배포에 편집 데이터 계층이 없어도 추천 전체가 못 뜨면 안 되므로
	 * {@code ObjectProvider} 다. 없으면 기준선 없이 원래대로 실패한다.
	 */
	private final ObjectProvider<EditorialPickBaselineProvider> editorialPickProvider;

	/**
	 * 단계가 넘어갈 때마다 진행률을 남기고 화면에 밀어 보낸다. 선택 의존성으로 두지 않는다 —
	 * 없으면 진행률이 조용히 사라지고 화면은 계산이 멈춘 것과 구분할 수 없다.
	 */
	private final JobProgressReporter progress;

	public RecommendationService(ObjectProvider<RecommendationEnginePort> enginePort,
			CandidateAssembler candidateAssembler, RecommendationRecorder recorder,
			RecommendationProperties properties, Clock clock,
			ObjectProvider<ItineraryDraftPort> itineraryDraftPort,
			ObjectProvider<EditorialPickBaselineProvider> editorialPickProvider,
			JobProgressReporter progress) {
		this.enginePort = enginePort;
		this.candidateAssembler = candidateAssembler;
		this.recorder = recorder;
		this.properties = properties;
		this.clock = clock;
		this.itineraryDraftPort = itineraryDraftPort;
		this.editorialPickProvider = editorialPickProvider;
		this.progress = progress;
	}

	/**
	 * @throws RecommendationFailedException 결과를 만들지 못했을 때. 던지기 전에 Job 은
	 *     FAILED 로 이미 저장돼 있고, 후보를 만들었다면 그 후보들도 함께 저장돼 있다
	 */
	public RecommendationResult recommend(RecommendationCommand command) {
		return continueJob(prepare(command), command);
	}

	/**
	 * Job 을 만들기만 한다 — 저장하지 않고, 엔진도 부르지 않는다. 빠르고, 실패하지 않는다
	 * ({@code jobType}·스냅샷 ID 검증은 이미 {@link RecommendationCommand} 생성자가 끝냈다).
	 *
	 * <p>비동기 러너 전용 진입점이다. 러너는 이 job 을 {@code PENDING} 으로 먼저 저장해 작업
	 * 번호를 즉시 돌려준 뒤 별도 스레드에서 {@link #continueJob} 으로 이어간다.
	 */
	public RecommendationJob prepare(RecommendationCommand command) {
		// 서버가 만든다. 클라이언트가 준 값을 쓰지 않는다.
		UUID requestId = UUID.randomUUID();
		UUID jobId = UUID.randomUUID();
		OffsetDateTime createdAt = OffsetDateTime.now(this.clock);

		RecommendationJob job = RecommendationJob.start(jobId, requestId, command.userId(), command.jobType(),
				createdAt);
		job.applyRequestContext(command.tripId(), command.tripVersion(), command.preferenceSnapshotId(),
				command.constraintSnapshotId(), command.itineraryId(), command.itineraryVersion(),
				command.baseVersion(), command.appVersion());
		return job;
	}

	/**
	 * 이미 만들어진 job 을 이어 실행한다 — 엔진 호출부터 저장까지. {@code recommend} 가 부를
	 * 때는 방금 만든(아직 저장 안 된) job 이고, 비동기 러너가 부를 때는 이미 {@code PENDING}
	 * 으로 저장된 job 이다 — 둘 다 여기부터는 같은 길을 간다.
	 *
	 * @throws RecommendationFailedException 결과를 만들지 못했을 때. 던지기 전에 Job 은
	 *     FAILED 로 이미 저장돼 있고, 후보를 만들었다면 그 후보들도 함께 저장돼 있다
	 */
	public RecommendationResult continueJob(RecommendationJob job, RecommendationCommand command) {
		// createdAt 은 job 이 처음 만들어진 시각이다 — 실행이 시작되는 시각이 아니다. 비동기
		// 러너에서는 대기열에 머문 시간만큼 둘이 다르고, abandon()·이벤트의 "요청 시각" 은
		// 언제나 이 값이어야 한다.
		OffsetDateTime createdAt = job.getCreatedAt();
		long startedNanos = System.nanoTime();

		int topK = (command.topK() == null) ? defaultTopKFor(job) : command.topK();

		RecommendationEnginePort engine = this.enginePort.getIfAvailable();
		if (engine == null) {
			// 기동은 됐지만 이 배포에는 엔진이 없다. 조용히 빈 결과를 주지 않는다 —
			// 다만 편집자가 고른 기준선이 있으면 그것을 내보낸다.
			RecommendationResult baseline = editorialPickFallback(job, command, topK, createdAt, startedNanos,
					RecommendationCodes.ERROR_ENGINE_NOT_CONFIGURED);
			if (baseline != null) {
				return baseline;
			}
			throw abandon(job, RecommendationCodes.ERROR_ENGINE_NOT_CONFIGURED, JobStage.CANDIDATE_GENERATION,
					false, false, createdAt, startedNanos, null);
		}

		EngineCandidateBatch batch;
		try {
			batch = engine.generate(new EngineRequest(job.getRequestId(), command.userId(), command.tripId(),
					command.tripVersion(), command.preferenceSnapshotId(), command.constraintSnapshotId(),
					command.itineraryId(), command.itineraryVersion(), topK, command.location()));
		}
		catch (RecommendationEngineException ex) {
			// 콜드스타트가 걸리는 자리다. 출발지 좌표가 없는 여행은 ENGINE_ORIGIN_MISSING 으로
			// 여기 오고(좌표를 지어내지 않는다), 그 요청에 빈 화면 대신 기준선을 준다.
			RecommendationResult baseline = editorialPickFallback(job, command, topK, createdAt, startedNanos,
					ex.getErrorCode());
			if (baseline != null) {
				return baseline;
			}
			// 후보가 0곳인 것은 다시 불러도 안 달라진다 — 자료가 들어와야 바뀌므로 재시도
			// 가능으로 표시하지 않는다.
			boolean retryable = !RecommendationCodes.ERROR_NO_CANDIDATES.equals(ex.getErrorCode());
			throw abandon(job, ex.getErrorCode(), JobStage.CANDIDATE_GENERATION, ex.isTimeout(), retryable,
					createdAt, startedNanos, ex);
		}
		catch (RuntimeException ex) {
			RecommendationResult baseline = editorialPickFallback(job, command, topK, createdAt, startedNanos,
					RecommendationCodes.ERROR_ENGINE_UNAVAILABLE);
			if (baseline != null) {
				return baseline;
			}
			throw abandon(job, RecommendationCodes.ERROR_ENGINE_UNAVAILABLE, JobStage.CANDIDATE_GENERATION,
					false, true, createdAt, startedNanos, ex);
		}

		// 후보가 만들어진 시각. 저장이 끝난 시각(completed_at)과 나누는 이유는, 저장이 느렸던
		// 요청과 추천 계산이 느렸던 요청을 나중에 구분할 수 있어야 하기 때문이다.
		OffsetDateTime generatedAt = OffsetDateTime.now(this.clock);

		this.progress.advance(job, JobStage.VERSION_RESOLUTION);

		List<String> missingVersions = resolveMissingVersions(batch.versions());
		if (!missingVersions.isEmpty()) {
			// 여기서 기본값을 넣지 않는다 — 재현할 수 없는 결과를 재현 가능한 것처럼 남기지
			// 않는다. 다시 불러도 같은 버전이 비어 있을 테니 retryable=false 다.
			throw abandon(job, RecommendationVersionsMissingException.ERROR_CODE, JobStage.VERSION_RESOLUTION,
					false, false, createdAt, startedNanos,
					new RecommendationVersionsMissingException(missingVersions));
		}

		job.applyVersions(batch.versions().modelVersion(), batch.versions().featureVersion(),
				batch.versions().ontologyVersion(), batch.versions().policyVersion(),
				batch.versions().datasetVersion(), this.properties.serviceVersion(),
				this.properties.deploymentEnvironment());

		// 엔진이 실제로 쓴 출발지의 파생값만 남긴다. 정밀 좌표를 담을 칸은 이 표에 없다.
		job.applyOrigin(batch.resolvedLocation());

		this.progress.advance(job, JobStage.RANKING);

		CandidateAssembly assembly;
		try {
			assembly = this.candidateAssembler.assemble(job.getRequestId(), batch, topK,
					this.properties.unknownExclusionThreshold(), this.properties.unspecifiedSeverity(),
					createdAt);
		}
		catch (SensitiveDataInPayloadException ex) {
			// 저장하지 않고 실패로 남긴다. 요청이 있었다는 사실과 왜 버렸는지는 남아야 하고,
			// 문제가 된 값 자체는 남으면 안 된다.
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
		// occurred_at 은 요청 시각이지 완료 시각이 아니다.
		OutboxAppendCommand event = buildRequestedEvent(job, createdAt, assembly.diversityMetrics());

		// 편집 Job(ITEM_REMOVE·ITINERARY_RECALCULATE)은 후보가 0건이어도 실패가 아니다.
		// "제외했더니 넣을 후보가 없다" 는 정답이고, 조건을 완화해 억지로 채우지 않는다.
		// 빈 자리는 판 경고 RECALC_NO_CANDIDATE 로 남는다. 새 일정 생성은 그대로 422 다.
		boolean editJob = command.isItineraryEdit();
		if (assembly.returnedCount() == 0 && !editJob) {
			// 실패지만 후보는 이미 다 만들어졌다. 하드 제약을 풀어 목록을 채우지 않는다.
			// 후보 행은 전부 저장한다 — "왜 빈손이었나" 는 그 행들에만 적혀 있다.
			JobStage stage = (assembly.generatedCount() == 0)
					? JobStage.CANDIDATE_GENERATION
					: JobStage.CONSTRAINT_EVALUATION;
			job.markFailed(RecommendationCodes.ERROR_NO_FEASIBLE_RESULT, stage, completedAt, false, false);
			this.recorder.record(job, assembly.candidates(),
					List.of(event, buildFailedEvent(job, completedAt)));
			throw new RecommendationFailedException(job.getRequestId(), job.getJobId(),
					RecommendationCodes.ERROR_NO_FEASIBLE_RESULT,
					stage, "반환할 수 있는 후보가 없다 (생성 " + assembly.generatedCount() + "건)", null);
		}

		// 이 보고는 markCompleted 앞이어야 한다. markCompleted 가 상태를 SUCCEEDED 로 바꾸면
		// 그 뒤의 단계 보고는 markStage 가 무시한다(끝난 작업의 진행률을 되돌리지 않는다).
		// 그래서 성공 경로에서는 PERSISTENCE 단계가 화면에 나가지 않고 85%에서 100%로 넘어간다.
		if (job.getJobType() == JobType.ITINERARY_GENERATION || editJob) {
			this.progress.advance(job, JobStage.ROUTE_OPTIMIZATION);
		}

		job.markCompleted(generatedAt, completedAt, batch.fallbackMode(), batch.fallbackReason());

		// 자리가 markCompleted 뒤·recorder 호출 앞인 것이 중요하다. Job 상태는 이미 SUCCEEDED
		// 지만 itineraryId 는 아직 없다 — attachItinerary 는 recorder.recordWithItinerary 안에서
		// 저장 직전에 불린다. 자세한 근거는 RecommendationJob.assertItineraryAttachedIfRequired().
		ItineraryDraft draft = null;
		if (job.getJobType() == JobType.ITINERARY_GENERATION) {
			ItineraryDraftPort port = this.itineraryDraftPort.getIfAvailable();
			if (port == null) {
				// 이 배포에 일정 조립기가 없다. 조용히 넘어가지 않는다 — ENGINE_NOT_CONFIGURED 와 같은 판단.
				throw abandon(job, RecommendationCodes.ERROR_ITINERARY_PORT_NOT_CONFIGURED, JobStage.PERSISTENCE,
						false, false, createdAt, startedNanos, null);
			}
			try {
				// 트랜잭션 밖이다. 여기서 실패해도 후보는 assembly 에 담겨 있어 abandon() 이
				// recordFailure 로 남긴다.
				draft = port.assemble(buildDraftCommand(job, assembly));
			}
			catch (RuntimeException ex) {
				throw abandon(job, RecommendationCodes.ERROR_ITINERARY_ASSEMBLY_FAILED, JobStage.ROUTE_OPTIMIZATION,
						false, false, createdAt, startedNanos, ex);
			}
		}

		// 편집 Job 은 새 일정을 만들지 않고 있는 판의 하루만 다시 채운다. revise 는 트랜잭션
		// 밖(읽기 + 순수 계산)이고 publish 는 recorder 트랜잭션 안이다. 게시 시점의
		// ItineraryPublishConflictException 은 여기서 잡지 않는다 — RecommendationJobWorker 가
		// ITINERARY_VERSION_CONFLICT 로 남긴다. 계산은 맞았고 저장만 늦은 것이라
		// ASSEMBLY_FAILED 와 섞으면 안 된다.
		ItineraryRevisionDraft revision = null;
		if (editJob) {
			ItineraryDraftPort port = this.itineraryDraftPort.getIfAvailable();
			if (port == null) {
				throw abandon(job, RecommendationCodes.ERROR_ITINERARY_PORT_NOT_CONFIGURED, JobStage.PERSISTENCE,
						false, false, createdAt, startedNanos, null);
			}
			try {
				revision = port.revise(buildRevisionCommand(job, command, assembly));
			}
			catch (RuntimeException ex) {
				throw abandon(job, RecommendationCodes.ERROR_ITINERARY_ASSEMBLY_FAILED, JobStage.ROUTE_OPTIMIZATION,
						false, false, createdAt, startedNanos, ex);
			}
		}

		// 이 목록은 recorder 의 같은 트랜잭션으로 들어간다 — 여기 담긴 것은 Job·후보와 같이
		// 남거나 같이 사라진다.
		List<OutboxAppendCommand> events = new ArrayList<>();
		events.add(event);
		itineraryRemoveEvent(job, command, createdAt).ifPresent(events::add);

		if (draft != null) {
			this.recorder.recordWithItinerary(job, assembly.candidates(), events, draft);
		}
		else if (revision != null) {
			this.recorder.recordWithItineraryRevision(job, assembly.candidates(), events, revision);
		}
		else {
			this.recorder.record(job, assembly.candidates(), events);
		}

		return new RecommendationResult(job.getRequestId(), job.getJobId(), job.getJobType(), job.getJobStatus(),
				job.getGeneratedAt(), assembly.returnedItems(), assembly.generatedCount(),
				assembly.eligibleCount(), assembly.returnedCount(), job.getFallbackMode(),
				job.getFallbackReason(), job.getSourceMode(), job.getModelVersion(), job.getFeatureVersion(),
				job.getOntologyVersion(), job.getPolicyVersion(), job.getDatasetVersion(),
				job.getServiceVersion(), job.getDeploymentEnvironment());
	}

	/**
	 * 개인화 추천을 못 만들었을 때 편집자가 고른 목록으로 결과를 만든다.
	 *
	 * <p>엔진 단계의 실패에서만 부른다 — 엔진 빈이 없을 때와 엔진이 던졌을 때다. 그 두
	 * 경우에만 후보가 하나도 없어 기준선으로 갈아탈 여지가 있다.
	 *
	 * <p>후보를 만들었는데 전부 걸러진 경우(422 NO_FEASIBLE_RESULT)에는 부르지 않는다. 하드
	 * 제약을 풀지 않는다는 원칙이 그대로 적용되고, 같은 {@code request_id} 에 Pick 후보를
	 * 겹쳐 넣으면 {@code uq_recommendation_candidate_request_place} 와 부딪히거나 탈락 이유가
	 * 덮인다.
	 *
	 * <p>일정 Job 에서도 부르지 않는다. Pick 은 장소 목록이지 시간이 배치된 일정이 아니어서,
	 * {@code itineraryId} 없는 성공이 되면
	 * {@code RecommendationJob.assertItineraryAttachedIfRequired} 와 어긋난다.
	 *
	 * @param fallbackReason 그대로 {@code fallback_reason} 에 남는다 — 신규 계정이라 온 것과
	 *     엔진이 죽어서 온 것을 가르는 값이다
	 * @return 기준선 결과, 또는 만들 수 없으면 {@code null}(부르는 쪽이 원래 실패로 돌아간다)
	 */
	private RecommendationResult editorialPickFallback(RecommendationJob job, RecommendationCommand command,
			int topK, OffsetDateTime createdAt, long startedNanos, String fallbackReason) {

		if (job.getJobType() == JobType.ITINERARY_GENERATION || command.isItineraryEdit()) {
			return null;
		}
		EditorialPickBaselineProvider provider = this.editorialPickProvider.getIfAvailable();
		if (provider == null) {
			return null;
		}

		EditorialPickBaseline baseline;
		try {
			baseline = provider.loadGlobalBaseline(command.constraintSnapshotId(), fallbackReason).orElse(null);
		}
		catch (RuntimeException ex) {
			// 기준선을 만들다 실패한 것이 원래 실패를 덮지 않게 한다. 부르는 쪽이 abandon 으로
			// 돌아가 진짜 원인(ENGINE_*)을 남긴다.
			return null;
		}
		if (baseline == null) {
			return null;
		}

		OffsetDateTime generatedAt = OffsetDateTime.now(this.clock);
		EngineCandidateBatch batch = baseline.batch();

		// 버전 검사를 건너뛰지 않는다 — 기준선도 재현할 수 있어야 한다.
		if (!resolveMissingVersions(batch.versions()).isEmpty()) {
			return null;
		}
		job.applyVersions(batch.versions().modelVersion(), batch.versions().featureVersion(),
				batch.versions().ontologyVersion(), batch.versions().policyVersion(),
				batch.versions().datasetVersion(), this.properties.serviceVersion(),
				this.properties.deploymentEnvironment());

		CandidateAssembly assembly;
		try {
			// 편집자가 정한 순서를 점수로 다시 매기지 않는다. 하드 제약 판정은 개인화 경로와
			// 같은 코드를 지난다.
			assembly = this.candidateAssembler.assembleFixedOrder(job.getRequestId(), batch, topK,
					this.properties.unknownExclusionThreshold(), this.properties.unspecifiedSeverity(),
					createdAt);
		}
		catch (RuntimeException ex) {
			return null;
		}
		if (assembly.returnedCount() == 0) {
			// Pick 의 장소가 전부 하드 제약에 걸렸다. 빈 목록을 성공으로 내보내지 않고 원래
			// 실패로 돌아간다.
			return null;
		}

		job.applyCounts(assembly.generatedCount(), assembly.eligibleCount(), assembly.returnedCount());
		job.applyLatencies(elapsedMs(startedNanos), batch.latencies().candidateGenerationMs(), null, null,
				null, null);
		job.fallBackToEditorialPick(baseline.pickId());

		OffsetDateTime completedAt = OffsetDateTime.now(this.clock);
		job.markCompleted(generatedAt, completedAt, FallbackMode.EDITORIAL_PICK, fallbackReason);

		this.recorder.record(job, assembly.candidates(),
				List.of(buildRequestedEvent(job, createdAt, assembly.diversityMetrics())));

		return new RecommendationResult(job.getRequestId(), job.getJobId(), job.getJobType(), job.getJobStatus(),
				job.getGeneratedAt(), assembly.returnedItems(), assembly.generatedCount(),
				assembly.eligibleCount(), assembly.returnedCount(), job.getFallbackMode(),
				job.getFallbackReason(), job.getSourceMode(), job.getModelVersion(), job.getFeatureVersion(),
				job.getOntologyVersion(), job.getPolicyVersion(), job.getDatasetVersion(),
				job.getServiceVersion(), job.getDeploymentEnvironment());
	}

	/**
	 * 요청이 개수를 안 줬을 때 몇 개를 낼까 — 설정 기본값과 <b>일정이 필요한 수</b> 중 큰 쪽.
	 *
	 * <h2>🔴 왜 설정값 하나로는 안 되나</h2>
	 *
	 * {@code topK} 는 이름 그대로 「응답에 담을 개수」인데, 그 목록이 그대로 일정을 채우는 데도
	 * 쓰인다({@link #buildDraftCommand}). 설정 기본값은 여행이 며칠인지 모르는 상수라
	 * <b>3일 × 하루 4곳 = 12자리에 후보 10개</b>가 되고, 거기서 밥집 상한에 걸려 몇 곳이 밀리면
	 * 하루가 2~3곳으로 줄어든다. 실측이 그랬다 — 3일 여행에 7곳 (S15P21E201-1450).
	 *
	 * <p>필요한 수를 여기서 계산하지 않고 일정 쪽에 묻는다. 하루 몇 곳인지는 여행의 기분이
	 * 정하고 그 규칙은 {@code ItineraryDraftService} 에 있다 — 두 벌이 되면 한쪽만 바뀐다.
	 *
	 * <p>요청이 {@code topK} 를 <b>준</b> 경우에는 손대지 않는다. 화면이 「다섯 개만」이라고
	 * 물었으면 그건 화면의 결정이다.
	 *
	 * <p>문이 안 붙은 배포에서는 설정 기본값 그대로다. 그 배포는 일정을 만들지 않으므로
	 * 채울 자리도 없다.
	 */
	private int defaultTopKFor(RecommendationJob job) {
		int configured = this.properties.defaultTopK();
		ItineraryDraftPort port = this.itineraryDraftPort.getIfAvailable();
		if (port == null || job.getTripId() == null) {
			return configured;
		}
		return Math.max(configured, port.placesNeeded(job.getTripId().toString()));
	}

	/**
	 * {@code assembly.returnedItems()} 는 이미 {@code finalRank} 오름차순이라 여기서 다시
	 * 정렬하지 않는다.
	 */
	private ItineraryDraftCommand buildDraftCommand(RecommendationJob job, CandidateAssembly assembly) {
		List<ItineraryDraftCommand.PlannedPlace> places = assembly.returnedItems().stream()
				.map((p) -> new ItineraryDraftCommand.PlannedPlace(p.placeId(), p.finalRank(),
						p.reasonCodes(), p.warningCodes(), p.category()))
				.toList();

		return new ItineraryDraftCommand(job.getRequestId(), job.getTripId().toString(), job.getUserId().toString(),
				places, job.getModelVersion(), job.getFeatureVersion(), job.getOntologyVersion(),
				job.getPolicyVersion(), job.getDatasetVersion());
	}

	/**
	 * 편집 Job 의 배치 입력. 순위 풀은 {@link #buildDraftCommand} 와 같은 finalRank 오름차순이다.
	 * 어느 일정·어느 판·어느 날인지는 요청(command.edit)과 Job(resource_id·base_version) 양쪽에
	 * 있는데 Job 쪽을 정본으로 읽는다 — 저장된 값이 나중에 GET 으로 보이는 값이라서다.
	 */
	private ItineraryRevisionCommand buildRevisionCommand(RecommendationJob job, RecommendationCommand command,
			CandidateAssembly assembly) {
		List<ItineraryDraftCommand.PlannedPlace> pool = assembly.returnedItems().stream()
				.map((p) -> new ItineraryDraftCommand.PlannedPlace(p.placeId(), p.finalRank(),
						p.reasonCodes(), p.warningCodes(), p.category()))
				.toList();
		RecommendationCommand.ItineraryEdit edit = command.edit();
		int dayIndex = edit.dayIndex() == null ? -1 : edit.dayIndex();
		return new ItineraryRevisionCommand(job.getRequestId(), job.getResourceId().toString(),
				job.getBaseVersion(), job.getUserId().toString(), job.getJobType(), dayIndex, edit.itemKey(),
				edit.newlyExcludedPlaceIds(), edit.operationalReason(), pool, job.getModelVersion(),
				job.getFeatureVersion(), job.getOntologyVersion(), job.getPolicyVersion(), job.getDatasetVersion());
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
	 * <p>모든 실패는 {@code recommendation_failed} 이벤트를 남긴다. 버전을 못 구했어도 남는다 —
	 * 그 이벤트는 버전을 요구하지 않는다. 버전까지 확보된 뒤의 실패라면
	 * {@code recommendation_requested} 도 함께 남긴다. 두 이벤트는 서로를 대체하지 않는다.
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
	 * 사용자가 일정에서 뺀 장소를 행동 신호로 남긴다.
	 *
	 * <p>{@code ItineraryRecalculationService.removeItem} 이 아니라 여기서 만든다. 그쪽에는
	 * 트랜잭션이 없어서 이벤트를 적으면 {@code recordFromServer} 가 자기 트랜잭션을 새로 열고
	 * Job 저장과 이벤트가 따로 커밋된다. {@code OutboxService.append} 의 {@code MANDATORY} 는
	 * 그 새 트랜잭션으로 충족되므로 예외도 안 나서, 깨진 것이 아무 데도 안 나타난다.
	 *
	 * <p>이 이벤트는 요청 시점이 아니라 제외가 실제로 반영되는 트랜잭션에서 난다. Job 이
	 * 실패하면 이벤트도 없다 — 일정이 그대로인데 거부 신호가 남으면 랭커가 일어나지 않은
	 * 일을 배운다.
	 *
	 * <p>🔴 <b>여기서 동의를 묻지 않는다.</b> 전에는 이 자리가 {@code collectsBehaviorOf} 를
	 * 직접 불렀다 — 이 경로가 {@code OutboxService} 를 직접 불러 수집 API 의 동의 검사를 안
	 * 지났기 때문이다. 지금은 그 검사가 {@code OutboxService} 입구에 있어
	 * ({@code BehaviorConsent}, S15P21E201-1096) <b>부르는 쪽이 알 필요가 없다.</b> 개인화를 끈
	 * 사람이면 이 명령은 만들어졌다가 입구에서 걸러지고, 같은 트랜잭션의 Job·후보는 그대로
	 * 커밋된다. 명령을 만드는 것 자체는 부작용이 없다.
	 */
	private Optional<OutboxAppendCommand> itineraryRemoveEvent(RecommendationJob job,
			RecommendationCommand command, OffsetDateTime occurredAt) {

		if (job.getJobType() != JobType.ITEM_REMOVE) {
			return Optional.empty();
		}
		RecommendationCommand.ItineraryEdit edit = command.edit();
		if (edit == null || edit.newlyExcludedPlaceIds().isEmpty()) {
			return Optional.empty();
		}

		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("job_id", job.getJobId());
		payload.put("itinerary_id", job.getItineraryId());
		payload.put("item_key", edit.itemKey());
		payload.put("day_index", edit.dayIndex());
		payload.put("place_ids", edit.newlyExcludedPlaceIds().stream().map(UUID::toString).toList());
		// 없으면 키를 지우지 않고 null 로 둔다 — "안 적었다" 와 "물어보지 않았다" 를 가른다.
		payload.put("operational_reason", edit.operationalReason());

		return Optional.of(new OutboxAppendCommand(
				UUID.randomUUID(),
				EventType.ITINERARY_REMOVE.wireName(),
				this.properties.eventVersion(),
				EventType.ITINERARY_REMOVE.aggregateType(),
				job.getTripId(),
				job.getUserId().toString(),
				payload,
				occurredAt,
				job.getRequestId(),
				job.getUserId(),
				job.getTripId(),
				Producer.SERVER));
	}

	private OutboxAppendCommand buildRequestedEvent(RecommendationJob job, OffsetDateTime occurredAt) {
		return buildRequestedEvent(job, occurredAt, null);
	}

	/**
	 * {@code recommendation_requested} envelope. 개인정보는 싣지 않는다 — 실명·이메일·전화번호·
	 * 정밀 좌표 대신 스냅샷 ID 와 익명 UUID 만 넣는다. 값이 없는 필드는 키를 지우지 않고
	 * {@code null} 로 남긴다: "안 보냈다" 와 "없었다" 는 다른 사실이다.
	 *
	 * @param diversityMetrics 재정렬 전·후 지표. recommendation_job 에는 담을 칸이 없고 요청마다
	 *     늘어나는 분석용 값이라 이벤트로 싣는다. 실패 경로에서는 {@code null} 이다
	 */
	private OutboxAppendCommand buildRequestedEvent(RecommendationJob job, OffsetDateTime occurredAt,
			Map<String, Object> diversityMetrics) {
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
		payload.put("diversity_metrics", diversityMetrics);

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
	 * <p>버전을 요구하지 않는다. 있으면 싣고 없으면 {@code null} 로 둔다 — 버전을 못 구한
	 * 실패도 남기는 것이 이 이벤트의 존재 이유다.
	 *
	 * <p>{@code occurred_at} 은 실패한 시각이고 요청 시각은 payload 의 {@code requested_at} 에
	 * 따로 싣는다. 둘을 빼면 얼마나 버티다 죽었는지가 나온다.
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
