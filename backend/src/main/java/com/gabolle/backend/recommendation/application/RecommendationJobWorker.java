package com.gabolle.backend.recommendation.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.gabolle.backend.recommendation.domain.JobStage;
import com.gabolle.backend.recommendation.domain.RecommendationJob;
import com.gabolle.backend.recommendation.repository.RecommendationJobRepository;

/**
 * S15P21E201-192 — 이미 PENDING 으로 저장된 job 을 실제로 실행한다.
 *
 * <p>🔴 {@link RecommendationJobRunner} 와 클래스를 나눈 이유 — {@code @Async} 는 Spring
 * AOP 프록시로 동작하는데, 같은 클래스 안에서 자기 메서드를 부르면({@code this.execute(...)})
 * 프록시를 지나지 않아 <b>조용히 동기로 실행된다.</b> {@link RecommendationRecorder} 가
 * {@code @Transactional} 때문에 같은 이유로 분리된 것과 같은 문제다. 부르는 쪽(runner)이
 * 이 빈을 주입받아 부르므로 프록시를 지난다.
 */
@Component
@Profile({ "db", "dev" })
public class RecommendationJobWorker {

	private static final Logger log = LoggerFactory.getLogger(RecommendationJobWorker.class);

	private final RecommendationJobRepository jobRepository;

	private final RecommendationService recommendationService;

	private final RecommendationRecorder recorder;

	private final Clock clock;

	public RecommendationJobWorker(RecommendationJobRepository jobRepository,
			RecommendationService recommendationService, RecommendationRecorder recorder, Clock clock) {
		this.jobRepository = jobRepository;
		this.recommendationService = recommendationService;
		this.recorder = recorder;
		this.clock = clock;
	}

	/**
	 * 🔴 이 메서드를 부르는 쪽은 <b>이미 PENDING 으로 저장된</b> job 을 넘겨야 한다 — 저장을
	 * 먼저 커밋해 두지 않으면, 이 메서드가 다른 스레드에서 먼저 도는 경우 아직 없는 행을
	 * 갱신하려 들 수 있다({@code RecommendationJobRunner.enqueue} 가 저장 → 이 메서드 호출
	 * 순서를 지킨다).
	 *
	 * <p>🔴 S15P21E201-604 — 예전에는 {@link RecommendationFailedException} 만 잡았다.
	 * 그 밖의 예외(예: 일정 저장 중 DB 예외)가 나면 여기서 그대로 삼켜지지 않고 스레드
	 * 밖으로 나가 <b>Job 이 RUNNING 인 채로 영원히 남았다</b> — 폴링하는 프론트가 무한히
	 * 기다리게 된다. 그래서 {@code RuntimeException} 도 잡아 Job 을 FAILED 로 확실히
	 * 남긴다.
	 */
	@Async("recommendationJobExecutor")
	public void execute(RecommendationJob job, RecommendationCommand command) {
		try {
			job.markRunning(JobStage.CANDIDATE_GENERATION);
			this.jobRepository.save(job);

			this.recommendationService.continueJob(job, command);
		}
		catch (RecommendationFailedException ex) {
			// 예상된 종료다 — RecommendationService.abandon() 안에서 이미 FAILED 로
			// 저장됐다. 여기는 백그라운드 스레드라 이 예외를 받아 줄 HTTP 응답이 없다 —
			// 삼키는 것이 맞다. 폴링하는 쪽이 GET /api/v1/jobs/{jobId} 로 실패 사유를 본다.
			log.warn("추천 Job 이 예상된 사유로 실패했습니다. jobId={}, requestId={}, errorCode={}",
					job.getJobId(), job.getRequestId(), ex.getErrorCode(), ex);
		}
		catch (RuntimeException ex) {
			// 🔴 예상하지 못한 실패 — 여기가 없으면 RUNNING 좀비가 남는다.
			log.error("추천 Job 실행 중 예기치 않은 오류가 발생했습니다. jobId={}, requestId={}",
					job.getJobId(), job.getRequestId(), ex);
			job.markFailed(RecommendationCodes.ERROR_UNEXPECTED, JobStage.PERSISTENCE,
					OffsetDateTime.now(this.clock), false, false);
			try {
				this.recorder.recordFailure(job, List.of());
			}
			catch (RuntimeException recordingFailure) {
				// 🔴 알려진 한계 — recordWithItinerary 트랜잭션이 attachItinerary 이후
				//    (예: 후보·이벤트 저장 단계)에서 실패해 롤백되면, 이 job 객체에는 이미
				//    롤백된(=DB 에는 없는) itineraryId 가 남아 있다. 그 값 그대로 저장을
				//    시도하면 fk_recommendation_job_itinerary 가 이 저장마저 거부할 수 있다.
				//    그래도 스레드를 죽게 두지 않는다 — 로그로 남기고 여기서 끝낸다.
				log.error("추천 Job 실패 기록마저 실패했습니다. jobId={}, requestId={}",
						job.getJobId(), job.getRequestId(), recordingFailure);
			}
		}
	}
}
