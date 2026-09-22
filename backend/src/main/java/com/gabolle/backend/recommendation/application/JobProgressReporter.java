package com.gabolle.backend.recommendation.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.gabolle.backend.recommendation.domain.JobStage;
import com.gabolle.backend.recommendation.domain.RecommendationJob;
import com.gabolle.backend.recommendation.repository.RecommendationJobRepository;

/**
 * 단계가 넘어갔다는 사실을 표에 저장하고 지금 보고 있는 연결에 알린다. 재접속했을 때 읽을
 * 정본은 표여야 하므로 둘을 함께 한다.
 *
 * 이 클래스의 모든 실패는 로그로만 남고 위로 던지지 않는다 — 진행률 보고가 실패했다고 추천
 * 계산까지 버리면 사용자가 일정을 못 받는다.
 */
@Component
@Profile({ "db", "dev" })
public class JobProgressReporter {

	private static final Logger log = LoggerFactory.getLogger(JobProgressReporter.class);

	private final RecommendationJobRepository jobRepository;

	private final JobProgressBroker broker;

	public JobProgressReporter(RecommendationJobRepository jobRepository, JobProgressBroker broker) {
		this.jobRepository = jobRepository;
		this.broker = broker;
	}

	/**
	 * 파이프라인이 다음 단계에 들어섰다. 저장을 먼저 하고 알린다 — 반대로 하면 다시 붙은
	 * 화면이 방금 본 값보다 낮은 값을 받는 순간이 생긴다.
	 */
	public void advance(RecommendationJob job, JobStage stage) {
		job.markStage(stage);
		save(job);
		publish(job);
	}

	/**
	 * 지금 상태를 알리기만 하고 저장하지 않는다. 시작과 끝 상태는 각각
	 * {@code RecommendationJobWorker} 와 결과·실패를 기록하는 트랜잭션이 이미 저장하므로,
	 * 여기서 또 저장하면 그 트랜잭션 밖에서 같은 행을 한 번 더 쓰게 된다.
	 */
	public void publishCurrent(RecommendationJob job) {
		publish(job);
	}

	private void save(RecommendationJob job) {
		try {
			this.jobRepository.save(job);
		}
		catch (RuntimeException ex) {
			log.warn("진행률 저장에 실패했습니다 — 계산은 계속합니다. jobId={}, stage={}",
					job.getJobId(), job.getJobStage(), ex);
		}
	}

	private void publish(RecommendationJob job) {
		try {
			this.broker.publish(new JobProgressBroker.JobProgressSnapshot(
					job.getJobId(), job.getJobStatus(),
					job.getJobStage() == null ? null : job.getJobStage().name(),
					job.getProgressPercent(), job.getErrorCode()));
		}
		catch (RuntimeException ex) {
			log.warn("진행률 전송에 실패했습니다 — 계산은 계속합니다. jobId={}", job.getJobId(), ex);
		}
	}
}
