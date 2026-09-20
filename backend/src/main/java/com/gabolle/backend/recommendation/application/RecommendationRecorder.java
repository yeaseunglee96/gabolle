package com.gabolle.backend.recommendation.application;

import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.event.application.OutboxAppendCommand;
import com.gabolle.backend.event.application.OutboxService;
import com.gabolle.backend.recommendation.application.port.ItineraryDraft;
import com.gabolle.backend.recommendation.application.port.ItineraryDraftPort;
import com.gabolle.backend.recommendation.application.port.ItineraryHandle;
import com.gabolle.backend.recommendation.application.port.ItineraryPublishConflictException;
import com.gabolle.backend.recommendation.application.port.ItineraryRevisionDraft;
import com.gabolle.backend.recommendation.domain.RecommendationCandidate;
import com.gabolle.backend.recommendation.domain.RecommendationJob;
import com.gabolle.backend.recommendation.repository.RecommendationCandidateRepository;
import com.gabolle.backend.recommendation.repository.RecommendationJobRepository;

/**
 * 추천 한 건의 저장을 한 트랜잭션으로 묶는다. Job·후보 전부·Outbox 이벤트가 같이 남거나 같이
 * 사라진다 — 후보 일부만 남으면 그 요청은 나중에 성공한 것처럼 보인다.
 *
 * 이 클래스가 {@code RecommendationService} 와 분리돼 있는 이유는 Spring 의
 * {@code @Transactional} 이 프록시(빈을 감싸는 대역 객체. 메서드 호출을 가로채 트랜잭션을
 * 열고 닫는다)로 동작하기 때문이다. 같은 클래스 안에서 자기 메서드를 부르면 프록시를
 * 지나지 않아 트랜잭션이 조용히 안 열린다.
 */
@Component
@Profile({ "db", "dev" })
public class RecommendationRecorder {

	private final RecommendationJobRepository jobRepository;

	private final RecommendationCandidateRepository candidateRepository;

	private final OutboxService outboxService;

	/**
	 * 추천만 스캔하는 시험 컨텍스트에는 이 포트의 구현이 빈으로 없어서 {@code ObjectProvider}
	 * 로 받는다 — {@code RecommendationService} 의 엔진 포트와 같은 판단이다.
	 */
	private final ObjectProvider<ItineraryDraftPort> itineraryDraftPort;

	public RecommendationRecorder(RecommendationJobRepository jobRepository,
			RecommendationCandidateRepository candidateRepository, OutboxService outboxService,
			ObjectProvider<ItineraryDraftPort> itineraryDraftPort) {
		this.jobRepository = jobRepository;
		this.candidateRepository = candidateRepository;
		this.outboxService = outboxService;
		this.itineraryDraftPort = itineraryDraftPort;
	}

	/**
	 * 후보를 실제로 만들어 낸 요청의 저장 경로. Job 이 {@code SUCCEEDED} 든 반환할 것이 없어
	 * {@code FAILED} 든 같은 길로 저장한다 — 하드 제약에 다 걸려 아무것도 못 낸 결과야말로
	 * 후보 행이 전부 남아 있어야 설명된다.
	 */
	@Transactional
	public void record(RecommendationJob job, List<RecommendationCandidate> candidates,
			List<OutboxAppendCommand> events) {
		// saveAndFlush — 후보의 request_id 는 Job 을 가리키는 외래키인데 JPA 연관관계가 아니라
		// 그냥 UUID 컬럼이라 Hibernate 는 이 의존을 모른다. 먼저 내보내지 않으면 삽입 순서에
		// 따라 외래키 위반이 날 수 있다. 같은 트랜잭션 안이라 flush 해도 원자성은 그대로다.
		this.jobRepository.saveAndFlush(job);
		// 후보도 여기서 내보낸다. 커밋 때까지 미루면 UNIQUE(request_id, place_id) 위반이
		// 트랜잭션이 끝나는 순간에야 터져서, 스택이 어느 후보 때문인지 못 가리킨다.
		this.candidateRepository.saveAllAndFlush(candidates);
		appendAll(events);
	}

	/**
	 * 추천이 실제로 일정을 만든 요청의 저장 경로. 일정·Job·후보·Outbox 가 한 트랜잭션이다.
	 * 나누면 후보 행은 완벽한 성공으로 읽히는데 담을 일정이 없는 상태가 생긴다.
	 * 순서가 일정 → Job → 후보인 이유는 {@code ck_recommendation_job_result_present} 가
	 * CHECK 제약이라 지연시킬 수 없기 때문이다. Job 을 먼저 저장하면 itinerary_id 가 빈 채로
	 * SUCCEEDED + ITINERARY_GENERATION 이 되어 그 CHECK 에 즉시 막힌다.
	 */
	@Transactional
	public void recordWithItinerary(RecommendationJob job, List<RecommendationCandidate> candidates,
			List<OutboxAppendCommand> events, ItineraryDraft draft) {
		ItineraryHandle handle = this.itineraryDraftPort.getObject().persist(draft);
		job.attachItinerary(handle.itineraryId(), handle.version());
		// 저장 직전에 본다 — 이유는 RecommendationJob.assertItineraryAttachedIfRequired().
		job.assertItineraryAttachedIfRequired();
		this.jobRepository.saveAndFlush(job);
		this.candidateRepository.saveAllAndFlush(candidates);
		appendAll(events);
	}

	/**
	 * 있는 일정의 하루를 다시 채운 요청의 저장 경로. {@link #recordWithItinerary} 의 형제이고
	 * 게시 → Job → 후보 순서와 그 이유도 같다. {@code publish} 가
	 * {@link ItineraryPublishConflictException} 을 던지면 이 트랜잭션 전체가 되돌려져 판도
	 * 항목도 Job 갱신도 후보도 이벤트도 안 남는다. 실패 기록은
	 * {@code RecommendationJobWorker} 가 {@link #recordFailure} 로 따로 남긴다.
	 */
	@Transactional
	public void recordWithItineraryRevision(RecommendationJob job, List<RecommendationCandidate> candidates,
			List<OutboxAppendCommand> events, ItineraryRevisionDraft revision) {
		ItineraryHandle handle = this.itineraryDraftPort.getObject().publish(revision);
		job.attachItinerary(handle.itineraryId(), handle.version());
		job.assertItineraryAttachedIfRequired();
		this.jobRepository.saveAndFlush(job);
		this.candidateRepository.saveAllAndFlush(candidates);
		appendAll(events);
	}

	/**
	 * 실패 경로. {@code REQUIRES_NEW} 인 것은 성공 트랜잭션이 깨진 뒤에도 실패 기록은 남아야
	 * 하기 때문이다 — 같은 트랜잭션에 실으면 롤백에 함께 쓸려 나간다.
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
