package com.gabolle.backend.event;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import com.gabolle.backend.event.application.OutboxAppendCommand;
import com.gabolle.backend.event.application.OutboxService;
import com.gabolle.backend.event.repository.EventOutboxRepository;
import com.gabolle.backend.recommendation.application.RecommendationCodes;
import com.gabolle.backend.recommendation.application.RecommendationRecorder;
import com.gabolle.backend.recommendation.domain.CandidateStage;
import com.gabolle.backend.recommendation.domain.ConstraintVerdict;
import com.gabolle.backend.recommendation.domain.FallbackMode;
import com.gabolle.backend.recommendation.domain.JobType;
import com.gabolle.backend.recommendation.domain.RecommendationCandidate;
import com.gabolle.backend.recommendation.domain.RecommendationJob;
import com.gabolle.backend.recommendation.repository.RecommendationCandidateRepository;
import com.gabolle.backend.recommendation.repository.RecommendationJobRepository;
import com.gabolle.backend.recommendation.support.PostgresIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Outbox 의 멱등성과, 업무 저장·이벤트 저장이 한 트랜잭션이라는 것.
 *
 * <p>인수인계 문서의 필수 테스트 6·12 를 여기서 본다.
 */
class OutboxTransactionIntegrationTest extends PostgresIntegrationTest {

	@Autowired
	private OutboxService outboxService;

	@Autowired
	private RecommendationRecorder recorder;

	@Autowired
	private EventOutboxRepository outboxRepository;

	@Autowired
	private RecommendationJobRepository jobRepository;

	@Autowired
	private RecommendationCandidateRepository candidateRepository;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@BeforeEach
	void clean() {
		this.candidateRepository.deleteAllInBatch();
		this.jobRepository.deleteAllInBatch();
		this.outboxRepository.deleteAllInBatch();
	}

	@Test
	@DisplayName("같은 event_id 로 두 번 적어도 행은 하나다")
	void sameEventIdIsAppendedOnlyOnce() {
		UUID eventId = UUID.randomUUID();
		UUID requestId = UUID.randomUUID();

		this.transactionTemplate.executeWithoutResult((status) -> {
			this.outboxService.append(event(eventId, requestId));
			this.outboxService.append(event(eventId, requestId));
		});

		assertThat(this.outboxRepository.countByEventType(RecommendationCodes.EVENT_RECOMMENDATION_REQUESTED))
				.isEqualTo(1);
	}

	@Test
	@DisplayName("트랜잭션 없이 부르면 조용히 넘어가지 않고 그 자리에서 막힌다")
	void appendOutsideATransactionFailsLoudly() {
		assertThatThrownBy(() -> this.outboxService.append(event(UUID.randomUUID(), UUID.randomUUID())))
				.isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
	}

	@Test
	@DisplayName("🔴 중간에 실패하면 Job · Candidate · Outbox 가 함께 롤백된다")
	void jobCandidatesAndOutboxRollBackTogether() {
		UUID requestId = UUID.randomUUID();
		UUID jobId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now();

		RecommendationJob job = RecommendationJob.start(jobId, requestId, UUID.randomUUID(),
				JobType.ITINERARY_GENERATION, now);
		// 성공한 Job 은 입력 스냅샷 없이 저장될 수 없다 — DB CHECK 가 같은 것을 요구한다.
		job.applyRequestContext(UUID.randomUUID(), 1, UUID.randomUUID(), UUID.randomUUID(), null, null, null,
				"app-1.0.0");
		job.applyVersions("m", "f", "o", "p", "d", "s", "test");
		job.applyCounts(1, 1, 1);
		job.markCompleted(now, now, FallbackMode.RULE, null);

		RecommendationCandidate candidate = RecommendationCandidate.builder()
				.candidateId(UUID.randomUUID())
				.requestId(requestId)
				.placeId(UUID.randomUUID())
				.candidateSource("ONTOLOGY_SEED")
				.candidateStage(CandidateStage.RETURNED)
				.eligible(true)
				.constraintVerdict(ConstraintVerdict.PASS)
				.finalScore(0.9)
				.finalRank(1)
				.returned(true)
				.createdAt(now)
				.build();

		// Job 과 후보가 저장된 <b>뒤</b> Outbox 단계에서 터지도록, 개인정보 검사에 걸리는
		// 이벤트를 일부러 넣는다. 실패 지점이 트랜잭션 중간이어야 이 테스트가 뜻을 갖는다.
		OutboxAppendCommand poisoned = new OutboxAppendCommand(UUID.randomUUID(),
				RecommendationCodes.EVENT_RECOMMENDATION_REQUESTED, 1, RecommendationCodes.AGGREGATE_TYPE,
				requestId, requestId.toString(), Map.of("contact_email", "someone@example.com"), now);

		assertThatThrownBy(() -> this.recorder.record(job, List.of(candidate), List.of(poisoned)))
				.isInstanceOf(RuntimeException.class);

		assertThat(this.jobRepository.findByRequestId(requestId)).isEmpty();
		assertThat(this.candidateRepository.countByRequestId(requestId)).isZero();
		assertThat(this.outboxRepository.count()).isZero();
	}

	private OutboxAppendCommand event(UUID eventId, UUID requestId) {
		return new OutboxAppendCommand(eventId, RecommendationCodes.EVENT_RECOMMENDATION_REQUESTED, 1,
				RecommendationCodes.AGGREGATE_TYPE, requestId, requestId.toString(),
				Map.of("request_id", requestId.toString()), OffsetDateTime.now());
	}
}
