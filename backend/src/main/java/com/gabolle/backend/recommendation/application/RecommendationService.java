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
import com.gabolle.backend.event.application.EventIngestService;
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
 * <p>🔴 컨트롤러는 {@code recommendation.presentation.RecommendationJobController} 에 있다
 * (S15P21E201-192). 이 첫 판이 지킨 것 — {@code data/error/meta.requestId} envelope(API-01),
 * {@code tripId} 소유·참여 관계 검증(FR-SEC-01, {@code TripQueryService} 를 그대로 재사용).
 *
 * <p>🔴 <b>정정 (2026-09-15, S15P21E201-967)</b> — 여기 <i>"아직 안 지킨 것 —
 * {@code Idempotency-Key}(API-09). 재시도로 같은 요청이 두 번 오면 Job 이 두 개 생긴다"</i>
 * 라고 적혀 있었다. <b>지금은 지킨다.</b> S15P21E201-944 가 넣었다 —
 * {@code RecommendationJobRunner.enqueue} 가 같은 키·같은 본문이면 있던 Job 을 그대로
 * 돌려주고, 같은 키를 <b>다른 본문</b>으로 재사용하면 409 를 낸다
 * ({@code RecommendationJobIdempotencyRepository} 가 표를 맡는다).
 *
 * <p>고친 자리가 <b>옆 클래스</b>라 이 줄이 안 지워졌고, 그 뒤 이 주석을 먼저 읽은 사람이
 * <b>끝난 일을 다시 하려 했다.</b> 낡은 주석은 없는 주석보다 나쁘다 — 읽는 사람이 그것을
 * 지금의 사실로 믿는다.
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

	/**
	 * 🔴 S15P21E201-604 — {@code RecommendationEnginePort} 와 같은 이유로
	 * {@code ObjectProvider} 다. {@code RecommendationSliceApplication} 이
	 * {@code itinerary} 패키지를 스캔하지 않아 그 슬라이스에는 이 포트가 없다.
	 */
	private final ObjectProvider<ItineraryDraftPort> itineraryDraftPort;

	/**
	 * Editor's Pick 기준선 (S15P21E201-555).
	 *
	 * <p>🔴 {@code RecommendationEnginePort} 와 같은 이유로 {@code ObjectProvider} 다 —
	 * 이 배포에 편집 데이터 계층이 없을 수 있고, 그때 <b>기준선이 없다는 이유로 추천
	 * 전체가 못 뜨면 안 된다.</b> 없으면 지금까지와 똑같이 실패한다.
	 */
	private final ObjectProvider<EditorialPickBaselineProvider> editorialPickProvider;

	/**
	 * 단계가 넘어갈 때마다 진행률을 남기고 화면에 밀어 보낸다 — S15P21E201-193.
	 *
	 * <p>선택 의존성으로 두지 않는다. 없으면 진행률이 조용히 사라지고, 화면은 계산이 멈춘
	 * 것과 구분할 수 없다.
	 */
	private final JobProgressReporter progress;

	/**
	 * 행동 개인화 동의를 묻는 통로 — S15P21E201-1080.
	 *
	 * <p>🔴 {@code ObjectProvider} 인 이유는 이 클래스의 다른 선택적 의존과 같다.
	 * {@code RecommendationSliceApplication}(추천만 스캔하는 시험 컨텍스트)은 {@code user}
	 * 패키지를 안 스캔해서 이 빈이 없을 수 있다. 없을 때 어떻게 하는지는
	 * {@link #collectsBehavior(java.util.UUID)} 에 적었다.
	 */
	private final ObjectProvider<EventIngestService> eventIngest;

	public RecommendationService(ObjectProvider<RecommendationEnginePort> enginePort,
			CandidateAssembler candidateAssembler, RecommendationRecorder recorder,
			RecommendationProperties properties, Clock clock,
			ObjectProvider<ItineraryDraftPort> itineraryDraftPort,
			ObjectProvider<EditorialPickBaselineProvider> editorialPickProvider,
			JobProgressReporter progress, ObjectProvider<EventIngestService> eventIngest) {
		this.enginePort = enginePort;
		this.candidateAssembler = candidateAssembler;
		this.recorder = recorder;
		this.properties = properties;
		this.clock = clock;
		this.itineraryDraftPort = itineraryDraftPort;
		this.editorialPickProvider = editorialPickProvider;
		this.progress = progress;
		this.eventIngest = eventIngest;
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
	 * <p>🔴 S15P21E201-192 비동기 러너 전용 진입점이다. 러너는 이 job 을 {@code PENDING}
	 * 으로 먼저 저장하고 클라이언트에게 작업 번호를 즉시 돌려준 뒤, 별도 스레드에서
	 * {@link #continueJob} 을 불러 이어간다 — {@code recommend} 처럼 한 호출 안에서
	 * 끝내면 클라이언트가 엔진 계산이 끝날 때까지 기다리게 된다.
	 */
	public RecommendationJob prepare(RecommendationCommand command) {
		// 🔴 서버가 만든다. 클라이언트가 준 값을 쓰지 않는다.
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
		// 🔴 createdAt 은 job 이 처음 만들어진 시각이다 — 지금(실행이 시작되는 시각)이 아니다.
		//    비동기 러너에서는 이 둘이 다를 수 있다(대기열에 머문 시간만큼). abandon()·이벤트가
		//    쓰는 "요청 시각" 은 언제나 이 값이어야 한다.
		OffsetDateTime createdAt = job.getCreatedAt();
		long startedNanos = System.nanoTime();

		int topK = (command.topK() == null) ? this.properties.defaultTopK() : command.topK();

		RecommendationEnginePort engine = this.enginePort.getIfAvailable();
		if (engine == null) {
			// 기동은 됐지만 이 배포에는 엔진이 없다. 조용히 빈 결과를 주지 않는다 —
			// 다만 편집자가 고른 기준선이 있으면 그것을 내보낸다 (S15P21E201-555).
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
			// 엔진이 잠깐 죽었거나 느렸을 수 있다 — 다시 부르면 달라질 여지가 있다.
			//
			// 🔴 여기가 콜드스타트가 실제로 걸리는 자리다. 출발지 좌표가 없는 여행은
			//    ENGINE_ORIGIN_MISSING 으로 여기 온다 — 좌표를 지어내지 않기 때문이다
			//    (BaselineRecommendationEngine 79~83행). 그 요청에 빈 화면 대신 기준선을 준다.
			RecommendationResult baseline = editorialPickFallback(job, command, topK, createdAt, startedNanos,
					ex.getErrorCode());
			if (baseline != null) {
				return baseline;
			}
			// 🔴 S15P21E201-827 — 후보가 0곳인 것은 다시 불러도 안 달라진다. 자료가 들어와야
			//    바뀌므로 재시도 가능으로 표시하지 않는다. 그 표시를 믿고 다시 부르는 쪽이
			//    같은 실패를 반복하게 된다.
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

		// S15P21E201-193 — 여기부터 엔진이 답한 버전을 확인한다. 단계 이름은 선언 순서가
		// 아니라 실제로 지나가는 순서를 따른다(JobStage javadoc).
		this.progress.advance(job, JobStage.VERSION_RESOLUTION);

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

		// 🔴 S15P21E201-550 — 엔진이 실제로 쓴 출발지의 **파생값만** 남긴다. 정밀 좌표를
		//    담을 칸은 이 표에 아예 없다(RecommendationJob.originAreaCode javadoc).
		job.applyOrigin(batch.resolvedLocation());

		this.progress.advance(job, JobStage.RANKING);

		CandidateAssembly assembly;
		try {
			assembly = this.candidateAssembler.assemble(job.getRequestId(), batch, topK,
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
		OutboxAppendCommand event = buildRequestedEvent(job, createdAt, assembly.diversityMetrics());

		// 🔴 S15P21E201-249 — 편집 Job(ITEM_REMOVE·ITINERARY_RECALCULATE)은 후보가 0건이어도
		//    실패가 아니다. "제외했더니 그 시간대에 넣을 후보가 없다" 는 정답이고, 조건을 완화해
		//    억지로 채우지 않는다(요구사항 3.2). 빈 자리는 판 경고 RECALC_NO_CANDIDATE 로 남는다 —
		//    항목 행이 없어 itinerary_item.warning_codes 에는 붙일 곳이 없다. 새 일정 생성은 그대로
		//    422 NO_FEASIBLE_RESULT 다 — 빈 일정은 결과가 아니다.
		boolean editJob = command.isItineraryEdit();
		if (assembly.returnedCount() == 0 && !editJob) {
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
			throw new RecommendationFailedException(job.getRequestId(), job.getJobId(),
					RecommendationCodes.ERROR_NO_FEASIBLE_RESULT,
					stage, "반환할 수 있는 후보가 없다 (생성 " + assembly.generatedCount() + "건)", null);
		}

		// S15P21E201-193 — 일정을 조립하는 Job 이면 그 일이 바로 아래에서 시작된다. 이 보고를
		// markCompleted 앞에 두는 이유가 있다: 저 호출이 상태를 SUCCEEDED 로 바꾸므로 그 뒤의
		// 단계 보고는 markStage 가 무시한다(끝난 작업의 진행률을 되돌리지 않기 위해서다).
		//
		// 🔴 그래서 성공 경로에서는 PERSISTENCE 단계가 화면에 나가지 않는다. 진행률 표시
		//    하나를 위해 "결과는 정해졌지만 일정 번호는 아직 없다" 는 기존 순서를 흔들지
		//    않는다 — 그 순서에는 이유가 적혀 있다(RecommendationJob.assertItineraryAttachedIfRequired).
		//    화면에서는 85%에서 100%로 넘어간다.
		if (job.getJobType() == JobType.ITINERARY_GENERATION || editJob) {
			this.progress.advance(job, JobStage.ROUTE_OPTIMIZATION);
		}

		job.markCompleted(generatedAt, completedAt, batch.fallbackMode(), batch.fallbackReason());

		// 🔴 S15P21E201-604 — ITINERARY_GENERATION 이고 반환할 후보가 있을 때만 일정을 조립한다.
		//    markCompleted 뒤·recorder 호출 앞이다: Job 상태는 이미 SUCCEEDED 로 정해졌지만
		//    itineraryId 는 아직 없다(attachItinerary 는 recorder.recordWithItinerary 안에서
		//    저장 직전에 불린다) — RecommendationJob.assertItineraryAttachedIfRequired() javadoc
		//    이 이 순서를 자세히 적어 뒀다.
		ItineraryDraft draft = null;
		if (job.getJobType() == JobType.ITINERARY_GENERATION) {
			ItineraryDraftPort port = this.itineraryDraftPort.getIfAvailable();
			if (port == null) {
				// 이 배포에 일정 조립기가 없다. 조용히 넘어가지 않는다 — ENGINE_NOT_CONFIGURED 와 같은 판단.
				throw abandon(job, RecommendationCodes.ERROR_ITINERARY_PORT_NOT_CONFIGURED, JobStage.PERSISTENCE,
						false, false, createdAt, startedNanos, null);
			}
			try {
				// 🔴 트랜잭션 밖이다. 여기서 실패해도 후보는 이미 assembly 에 담겨 있어
				//    abandon() 이 recordFailure 로 그것들을 남긴다 — 추천 자체가 성공했다는
				//    사실은 지워지지 않는다.
				draft = port.assemble(buildDraftCommand(job, assembly));
			}
			catch (RuntimeException ex) {
				throw abandon(job, RecommendationCodes.ERROR_ITINERARY_ASSEMBLY_FAILED, JobStage.ROUTE_OPTIMIZATION,
						false, false, createdAt, startedNanos, ex);
			}
		}

		// 🔴 S15P21E201-249 — 편집 Job 은 새 일정을 만들지 않고 있는 판의 하루만 다시 채운다.
		//    revise 는 트랜잭션 밖(읽기 + 순수 계산)이고 publish 는 recorder 트랜잭션 안이다 —
		//    ItineraryDraftPort javadoc 의 "두 쌍" 이 이것이다. 게시 시점의 CAS 충돌
		//    (ItineraryPublishConflictException)은 여기서 잡지 않는다 — RecommendationJobWorker 가
		//    받아 ITINERARY_VERSION_CONFLICT 로 남긴다. 계산은 맞았고 저장만 늦은 것이라
		//    ASSEMBLY_FAILED 와 섞으면 안 된다.
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

		// 🔴 S15P21E201-1080 — 여기서 이벤트 목록을 만든다. 이 셋은 recorder 의 같은
		//    트랜잭션으로 들어가므로, 여기 담긴 것은 Job·후보와 같이 남거나 같이 사라진다.
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
	 * 개인화 추천을 못 만들었을 때 편집자가 고른 목록으로 결과를 만든다 (S15P21E201-555).
	 *
	 * <p>FR-REC-03 · FR-REC-11 · FR-REC-15. 완료 기준 <i>"신규 계정과 취향 전부 SKIPPED
	 * 계정의 첫 결과가 비어 있지 않다"</i> 와 <i>"추천 서버 장애 시 하드 제약을 지키는
	 * 기준선 결과가 나온다"</i> 가 이 경로다.
	 *
	 * <h2>🔴 어디서 부르고 어디서 안 부르나</h2>
	 *
	 * <p><b>엔진 단계의 실패에서만 부른다</b> — 엔진 빈이 없을 때와 엔진이 던졌을 때다.
	 * 그 두 경우에는 후보가 하나도 만들어지지 않았으므로 기준선으로 갈아탈 여지가 있다.
	 *
	 * <p><b>후보를 만들었는데 전부 걸러진 경우(422 NO_FEASIBLE_RESULT)에는 부르지 않는다.</b>
	 * 이유가 둘이다. 하나는 그 상황의 정답이 "조건에 맞는 곳이 없다" 이고 하드 제약을 슬쩍
	 * 풀지 않는다는 원칙이 그대로 적용된다는 것이다. 다른 하나는 기록이다 — 그 요청의 후보
	 * 행들이 "왜 빈손이었나" 를 설명하는 유일한 자료인데, 같은 {@code request_id} 에 Pick
	 * 후보를 겹쳐 넣으면 {@code uq_recommendation_candidate_request_place} 와 부딪히거나
	 * (Pick 장소가 후보에도 있었을 때) 탈락 이유가 덮인다.
	 *
	 * <p><b>일정 Job 에서도 부르지 않는다.</b> Pick 은 장소 목록이지 시간이 배치된 일정이
	 * 아니다. {@code ITINERARY_GENERATION} 에 목록만 돌려주면 {@code itineraryId} 없는
	 * 성공이 되고, {@code RecommendationJob.assertItineraryAttachedIfRequired} 가 요구하는
	 * 것과 어긋난다.
	 *
	 * @param fallbackReason 왜 기준선으로 왔는가. 그대로 {@code fallback_reason} 에 남는다 —
	 *     신규 계정이라 온 것과 엔진이 죽어서 온 것을 가르는 값이다
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
			// 🔴 기준선을 만들다 실패한 것이 <b>원래 실패를 덮지 않게</b> 한다. 부르는 쪽이
			//    abandon 으로 돌아가 진짜 원인(ENGINE_*)을 남긴다 — 여기서 던지면 사용자는
			//    "기준선 오류" 를 보고 엔진이 죽은 사실은 기록에서 사라진다.
			return null;
		}
		if (baseline == null) {
			return null;
		}

		OffsetDateTime generatedAt = OffsetDateTime.now(this.clock);
		EngineCandidateBatch batch = baseline.batch();

		// 🔴 버전 검사를 건너뛰지 않는다. 기준선도 재현할 수 있어야 하고, 못 하면 결과를
		//    내보내지 않는 것이 이 저장소의 규칙이다(위 resolveMissingVersions 주석).
		if (!resolveMissingVersions(batch.versions()).isEmpty()) {
			return null;
		}
		job.applyVersions(batch.versions().modelVersion(), batch.versions().featureVersion(),
				batch.versions().ontologyVersion(), batch.versions().policyVersion(),
				batch.versions().datasetVersion(), this.properties.serviceVersion(),
				this.properties.deploymentEnvironment());

		CandidateAssembly assembly;
		try {
			// 🔴 assembleFixedOrder 다 — 편집자가 정한 순서를 점수로 다시 매기지 않는다.
			//    하드 제약 판정은 개인화 경로와 같은 코드를 지난다.
			assembly = this.candidateAssembler.assembleFixedOrder(job.getRequestId(), batch, topK,
					this.properties.unknownExclusionThreshold(), this.properties.unspecifiedSeverity(),
					createdAt);
		}
		catch (RuntimeException ex) {
			return null;
		}
		if (assembly.returnedCount() == 0) {
			// 🔴 Pick 의 장소가 전부 하드 제약에 걸렸다. 빈 목록을 성공으로 내보내지 않고
			//    원래 실패로 돌아간다 — 완료 기준이 요구하는 것은 "비어 있지 않은 결과" 다.
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
	 * 🔴 {@code assembly.returnedItems()} 는 이미 {@code finalRank} 오름차순이다
	 * ({@code CandidateAssembler.assemble} 이 순위 매긴 순서대로 담는다) — 여기서 다시
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
	 * 🔴 S15P21E201-249 — 편집 Job 의 배치 입력. 순위 풀은 {@link #buildDraftCommand} 와 같은
	 * 순서(finalRank 오름차순)다. 어느 일정·어느 판·어느 날인지는 요청(command.edit)과
	 * Job(resource_id·base_version)에 이미 있고, 둘은 {@code RecommendationJobRunner.enqueue}
	 * 가 같은 값으로 채웠다 — 여기서는 Job 쪽을 정본으로 읽는다. 저장된 값이 나중에 GET 으로
	 * 보이는 값이라서다.
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
	/**
	 * 사용자가 일정에서 뺀 장소를 행동 신호로 남긴다 — S15P21E201-1080.
	 *
	 * <h2>🔴 왜 {@code removeItem} 이 아니라 여기인가</h2>
	 *
	 * {@code ItineraryRecalculationService.removeItem} 쪽에는 <b>트랜잭션이 없다.</b> 실수가
	 * 아니라 의도다 — {@code RecommendationJobRunner} 는 {@code jobRepository.save(job)} 가
	 * <b>완전히 커밋된 뒤에</b> 비동기 워커를 넘겨야 해서 {@code @Transactional} 을 일부러 안
	 * 단다. 거기서 이벤트를 적으면 {@code recordFromServer} 가 <b>자기 트랜잭션을 새로 열고</b>,
	 * Job 저장과 이벤트가 각각 따로 커밋된다. {@code OutboxService.append} 의 {@code MANDATORY}
	 * 는 그 새 트랜잭션으로 <b>충족되므로 예외도 안 난다</b> — 깨진 것이 아무 데도 안 나타난다.
	 *
	 * <p>그래서 이미 트랜잭션이 있는 이 자리에서 만든다. {@code RecommendationRecorder} 가
	 * Job·후보·이벤트를 <b>한 트랜잭션</b>으로 넣는다.
	 *
	 * <h2>🔴 그래서 「뺐다」가 아니라 「빠졌다」를 적는다</h2>
	 *
	 * 이 이벤트는 요청 시점이 아니라 <b>제외가 실제로 반영되는 트랜잭션</b>에서 난다. Job 이
	 * 실패하면 이벤트도 없다. 그게 맞다 — 일정이 그대로인데 「이 장소를 거부했다」가 남으면
	 * 랭커는 일어나지 않은 일을 배운다.
	 *
	 * <h2>🔴 개인화를 끈 사람은 여기서 거른다</h2>
	 *
	 * 이 경로는 {@code OutboxService} 를 직접 부르므로 {@code EventIngestService} 안의 동의
	 * 검사를 <b>안 지난다.</b> 그래서 명령을 만들기 전에 직접 묻는다. 판정 규칙을 여기에 다시
	 * 쓰지 않고 {@code collectsBehaviorOf} 를 부르는 것이 핵심이다 — 두 벌이 되면 한쪽만 바뀐다.
	 *
	 * <p>🔴 <b>우회로 자체는 안 막았다.</b> 입구에서 막는 것은 S15P21E201-1096 이다. 그때까지는
	 * 이 경로로 <b>행동 신호를 하나 더 흘리면 그것도 동의를 안 본다.</b>
	 *
	 * <p>🔴 <b>빈이 없으면 안 적는다</b>({@code getIfAvailable() == null}). 동의를 확인할 수
	 * 없는데 적는 것보다 안 적는 쪽이 맞다 — {@code collectsBehaviorOf} 가 「계정을 못 찾으면
	 * 안 적는다」로 정한 것과 같은 판단이다. 실패는 조용한 수집이 아니라 빈 자리로 나타나야 한다.
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
		EventIngestService ingest = this.eventIngest.getIfAvailable();
		if (ingest == null || !ingest.collectsBehaviorOf(job.getUserId())) {
			return Optional.empty();
		}

		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("job_id", job.getJobId());
		payload.put("itinerary_id", job.getItineraryId());
		payload.put("item_key", edit.itemKey());
		payload.put("day_index", edit.dayIndex());
		payload.put("place_ids", edit.newlyExcludedPlaceIds().stream().map(UUID::toString).toList());
		// 🔴 사용자가 적은 이유를 그대로 싣는다. 없으면 키를 지우지 않고 null 로 둔다 —
		//    "안 적었다" 와 "물어보지 않았다" 를 나중에 가를 수 있어야 한다.
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
	 * @param diversityMetrics S15P21E201-548 의 재정렬 전·후 지표. 🔴 <b>이벤트에 싣는
	 *     이유</b> — 완료 기준이 "재정렬 전후 성능·다양성 지표가 함께 남는다" 인데,
	 *     recommendation_job 에는 이것을 담을 칸이 없다. 칸을 새로 만들면 요청마다 하나씩
	 *     늘어나는 값을 정규화된 표에 넣는 셈이고, 이 값은 <b>분석용</b>이라 이벤트 흐름이
	 *     제자리다. 실패 경로에서는 {@code null} 이다 — 잴 것이 없었다
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
		// 🔴 키를 지우지 않고 null 로 남긴다 — "안 보냈다" 와 "없었다" 는 다른 사실이다
		//    (이 이벤트의 다른 칸들과 같은 규칙).
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
