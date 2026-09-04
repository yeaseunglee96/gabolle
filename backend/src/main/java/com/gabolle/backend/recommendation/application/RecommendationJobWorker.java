package com.gabolle.backend.recommendation.application;

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

	private final RecommendationJobRepository jobRepository;

	private final RecommendationService recommendationService;

	public RecommendationJobWorker(RecommendationJobRepository jobRepository,
			RecommendationService recommendationService) {
		this.jobRepository = jobRepository;
		this.recommendationService = recommendationService;
	}

	/**
	 * 🔴 이 메서드를 부르는 쪽은 <b>이미 PENDING 으로 저장된</b> job 을 넘겨야 한다 — 저장을
	 * 먼저 커밋해 두지 않으면, 이 메서드가 다른 스레드에서 먼저 도는 경우 아직 없는 행을
	 * 갱신하려 들 수 있다({@code RecommendationJobRunner.enqueue} 가 저장 → 이 메서드 호출
	 * 순서를 지킨다).
	 */
	@Async("recommendationJobExecutor")
	public void execute(RecommendationJob job, RecommendationCommand command) {
		job.markRunning(JobStage.CANDIDATE_GENERATION);
		this.jobRepository.save(job);

		try {
			this.recommendationService.continueJob(job, command);
		}
		catch (RecommendationFailedException ex) {
			// 🔴 RecommendationService.abandon() 안에서 이미 FAILED 로 저장됐다. 여기는
			//    백그라운드 스레드라 이 예외를 받아 줄 HTTP 응답이 없다 — 삼키는 것이 맞다.
			//    폴링하는 쪽이 GET /api/v1/jobs/{jobId} 로 실패 사유를 본다.
		}
	}
}
