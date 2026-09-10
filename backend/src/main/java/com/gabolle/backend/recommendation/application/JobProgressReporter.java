package com.gabolle.backend.recommendation.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.gabolle.backend.recommendation.domain.JobStage;
import com.gabolle.backend.recommendation.domain.RecommendationJob;
import com.gabolle.backend.recommendation.repository.RecommendationJobRepository;

/**
 * 단계가 넘어갔다는 사실을 <b>남기고 알린다</b> — S15P21E201-193.
 *
 * <p>두 가지를 함께 해야 해서 자리를 따로 뒀다.
 * <ol>
 *   <li><b>남긴다</b> — 진행률을 표에 저장한다. 이것이 없으면 화면이 끊겼다 다시 붙었을 때
 *       읽을 곳이 없다. 열려 있는 연결의 기억은 프로세스 안에만 있으므로 재접속의 정본은
 *       표여야 한다</li>
 *   <li><b>알린다</b> — 지금 그 작업을 보고 있는 연결에 밀어 보낸다</li>
 * </ol>
 *
 * <p>도메인 객체({@link RecommendationJob})가 스스로 저장하지 않는다는 이 저장소의 규칙을
 * 지킨다 — 단계 계산은 도메인이 하고, 저장과 알림은 여기가 한다.
 *
 * <h2>🔴 진행률 보고가 추천 계산을 실패시키지 않는다</h2>
 * 이 클래스의 모든 실패는 로그로만 남는다. 진행률은 <b>계산의 결과가 아니라 그 계산을 보는
 * 창</b>이다. 창이 깨졌다고 계산을 버리면 사용자는 일정을 못 받는다 — 진행률 없이 결과를
 * 받는 편이 훨씬 낫다. 그래서 저장이 실패해도, 보내다 실패해도 위로 던지지 않는다.
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
	 * 파이프라인이 다음 단계에 들어섰다.
	 *
	 * <p>저장을 먼저 하고 알리는 순서다. 반대로 하면 화면이 받은 진행률이 표에 아직 없는
	 * 순간이 생기고, 그 사이에 다시 붙은 화면은 방금 본 값보다 낮은 값을 받는다.
	 */
	public void advance(RecommendationJob job, JobStage stage) {
		job.markStage(stage);
		save(job);
		publish(job);
	}

	/**
	 * 지금 상태를 알리기만 한다 — 저장은 하지 않는다.
	 *
	 * <p>시작할 때와 끝났을 때가 이 자리다. 두 상태는 <b>이미 다른 곳에서 저장된다</b> —
	 * 시작은 {@code RecommendationJobWorker} 가, 끝은 결과·실패를 기록하는 트랜잭션이 각자
	 * 저장한다. 여기서 또 저장하면 그 트랜잭션 밖에서 같은 행을 한 번 더 쓰는 셈이 된다.
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
