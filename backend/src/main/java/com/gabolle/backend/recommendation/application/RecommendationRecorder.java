package com.gabolle.backend.recommendation.application;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.event.application.OutboxAppendCommand;
import com.gabolle.backend.event.application.OutboxService;
import com.gabolle.backend.recommendation.domain.RecommendationCandidate;
import com.gabolle.backend.recommendation.domain.RecommendationJob;
import com.gabolle.backend.recommendation.repository.RecommendationCandidateRepository;
import com.gabolle.backend.recommendation.repository.RecommendationJobRepository;

/**
 * 추천 한 건의 저장을 <b>한 트랜잭션으로</b> 묶는다.
 *
 * <p>Job · 후보 전부 · Outbox 이벤트가 같이 남거나 같이 사라진다. 중간에 실패해서 후보 일부만
 * 남으면 그 요청은 나중에 <b>성공한 것처럼</b> 보이는데, 개수가 맞지 않는다는 것을 알아챌
 * 방법이 없다.
 *
 * <p>🔴 이 클래스가 {@code RecommendationService} 와 분리돼 있는 이유는 Spring 의
 * {@code @Transactional} 이 <b>프록시</b>(**빈을 감싸는 대역 객체. 메서드 호출을 가로채
 * 트랜잭션을 열고 닫는다**)로 동작하기 때문이다. 같은 클래스 안에서 자기 메서드를 부르면
 * 프록시를 지나지 않아 트랜잭션이 <b>조용히 안 열린다.</b>
 */
@Component
@Profile({ "db", "dev" })
public class RecommendationRecorder {

	private final RecommendationJobRepository jobRepository;

	private final RecommendationCandidateRepository candidateRepository;

	private final OutboxService outboxService;

	public RecommendationRecorder(RecommendationJobRepository jobRepository,
			RecommendationCandidateRepository candidateRepository, OutboxService outboxService) {
		this.jobRepository = jobRepository;
		this.candidateRepository = candidateRepository;
		this.outboxService = outboxService;
	}

	/**
	 * 후보를 실제로 만들어 낸 요청의 저장 경로. 셋이 한 트랜잭션이다.
	 *
	 * <p>🔴 Job 이 {@code SUCCEEDED} 든, 반환할 것이 없어 {@code FAILED} 든 <b>같은 길로 저장한다.</b>
	 * "하드 제약에 다 걸려서 아무것도 못 냈다" 는 결과야말로 후보 행이 전부 남아 있어야
	 * 설명되는 경우다 — 그때 후보를 안 남기면 왜 빈손이었는지 영영 못 묻는다.
	 */
	@Transactional
	public void record(RecommendationJob job, List<RecommendationCandidate> candidates,
			List<OutboxAppendCommand> events) {
		// 🔴 saveAndFlush — 후보의 request_id 는 Job 을 가리키는 외래키인데, JPA 연관관계가
		//    아니라 그냥 UUID 컬럼이라 Hibernate 는 이 의존을 모른다. Job 을 먼저 확실히
		//    내보내지 않으면 삽입 순서에 따라 외래키 위반이 날 수 있다.
		//    같은 트랜잭션 안이므로 flush 해도 원자성은 그대로다.
		this.jobRepository.saveAndFlush(job);
		// 후보도 여기서 내보낸다. 커밋 때까지 미루면 UNIQUE(request_id, place_id) 위반이
		// 트랜잭션이 끝나는 순간에야 터져서, 스택이 어느 후보 때문인지 못 가리킨다.
		this.candidateRepository.saveAllAndFlush(candidates);
		appendAll(events);
	}

	/**
	 * 실패 경로.
	 *
	 * <p>🔴 {@code REQUIRES_NEW} — 성공 트랜잭션이 도중에 깨진 뒤에도 실패 기록만은 남아야 한다.
	 * 같은 트랜잭션에 실으면 롤백에 함께 쓸려 나가 "왜 실패했는지" 가 아무 데도 안 남는다.
	 *
	 * @param events 이 실패에 대해 남길 이벤트. 실패는 언제나 {@code recommendation_failed} 를
	 *     남기고, 버전까지 확보된 뒤의 실패라면 {@code recommendation_requested} 도 함께 남는다
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void recordFailure(RecommendationJob job, List<OutboxAppendCommand> events) {
		this.jobRepository.save(job);
		appendAll(events);
	}

	private void appendAll(List<OutboxAppendCommand> events) {
		if (events == null) {
			return;
		}
		for (OutboxAppendCommand event : events) {
			this.outboxService.append(event);
		}
	}
}
