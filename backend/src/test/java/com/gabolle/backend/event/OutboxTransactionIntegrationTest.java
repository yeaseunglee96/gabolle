package com.gabolle.backend.event;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import com.gabolle.backend.event.application.EventIngestService;
import com.gabolle.backend.event.application.OutboxAppendCommand;
import com.gabolle.backend.event.application.OutboxService;
import com.gabolle.backend.event.domain.EventOutbox;
import com.gabolle.backend.event.domain.EventType;
import com.gabolle.backend.event.domain.Producer;
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
	private EventIngestService eventIngestService;

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

	@Autowired
	private JdbcTemplate jdbcTemplate;

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
	@DisplayName("envelope 값과 DB 수신 순번은 전용 컬럼에 저장된다")
	void envelopeColumnsAndSequenceArePersisted() {
		UUID recommendationEventId = UUID.randomUUID();
		UUID recommendationRequestId = UUID.randomUUID();
		UUID tripEventId = UUID.randomUUID();
		UUID tripRequestId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		UUID tripId = UUID.randomUUID();

		this.transactionTemplate.executeWithoutResult((status) -> {
			this.outboxService.append(new OutboxAppendCommand(recommendationEventId,
					RecommendationCodes.EVENT_RECOMMENDATION_REQUESTED, 1, RecommendationCodes.AGGREGATE_TYPE,
					recommendationRequestId, userId.toString(), Map.of("event_kind", "recommendation-column-test"),
					OffsetDateTime.now(), null, userId, tripId, Producer.SERVER));
			this.outboxService.append(new OutboxAppendCommand(tripEventId, "trip_created", 1, "trip", tripId,
					userId.toString(), Map.of("event_kind", "trip-column-test"), OffsetDateTime.now(), tripRequestId,
					userId, tripId, Producer.SERVER));
		});

		EventOutbox recommendation = this.outboxRepository.findById(recommendationEventId).orElseThrow();
		assertThat(recommendation.getRequestId()).isNull();
		assertThat(recommendation.getAggregateId()).isEqualTo(recommendationRequestId);
		assertThat(recommendation.getUserId()).isEqualTo(userId);
		assertThat(recommendation.getTripId()).isEqualTo(tripId);
		assertThat(recommendation.getProducer()).isEqualTo(Producer.SERVER);

		EventOutbox trip = this.outboxRepository.findById(tripEventId).orElseThrow();
		assertThat(trip.getRequestId()).isEqualTo(tripRequestId);

		Map<String, Object> row = this.jdbcTemplate.queryForMap(
				"SELECT request_id, user_id, trip_id, producer, seq FROM event_outbox WHERE event_id = ?", tripEventId);
		assertThat(row).containsEntry("request_id", tripRequestId)
				.containsEntry("user_id", userId)
				.containsEntry("trip_id", tripId)
				.containsEntry("producer", "SERVER")
				.containsKey("seq");
	}

	@Test
	@DisplayName("🔴 앱이 보낸 저장이 진짜 DB 에 사용자 축으로 적힌다 — 여행도 추천 요청도 없이")
	void aClientSaveWithoutTripOrRequestLandsOnTheUserAxis() {
		// 🔴 이 확인은 PostgreSQL 에서만 뜻이 있다. 지금까지 aggregate_type 에 들어간 값은
		//    'recommendation' 과 'trip' 둘뿐이었고, 'user' 가 실제로 저장되는지는 아무도
		//    안 재봤다. 컬럼 길이·제약이 막으면 앱 화면에서야 알게 된다 (S15P21E201-735).
		UUID eventId = UUID.randomUUID();

		// 🔴 계정을 실제로 만든다 (S15P21E201-549). 행동 관찰 이벤트는 그 사람이 행동 개인화를
		//    켜 뒀을 때만 적힌다. 전에는 아무 UUID 나 주체로 넘겨도 적혔는데, 이제 계정을 못
		//    찾으면 <b>동의를 확인할 수 없으므로 안 적는 쪽</b>이라 이 검사가 빈 표를 보게 된다.
		//    이 검사가 재려는 것은 aggregate_type='user' 가 실제로 저장되는가이지 동의가 아니다.
		UUID userId = behaviorEnabledUser();

		this.transactionTemplate.executeWithoutResult((status) -> this.eventIngestService.ingestFromClient(
				eventId, EventType.PLACE_LIKE, 1, userId, null, null,
				OffsetDateTime.now().minusSeconds(1), Map.of("place_id", "seomyeon-1", "surface", "home")));

		EventOutbox stored = this.outboxRepository.findById(eventId).orElseThrow();
		assertThat(stored.getAggregateId()).isEqualTo(userId);
		assertThat(stored.getUserId()).isEqualTo(userId);
		assertThat(stored.getTripId()).isNull();
		assertThat(stored.getRequestId()).isNull();
		assertThat(stored.getProducer()).isEqualTo(Producer.CLIENT);

		Map<String, Object> row = this.jdbcTemplate.queryForMap(
				"SELECT aggregate_type, aggregate_id, partition_key, producer FROM event_outbox WHERE event_id = ?",
				eventId);
		assertThat(row).containsEntry("aggregate_type", "user")
				.containsEntry("aggregate_id", userId)
				.containsEntry("partition_key", userId.toString())
				.containsEntry("producer", "CLIENT");
	}

	@Test
	@DisplayName("🔴 S15P21E201-947 — OutboxAppendCommand 를 거치지 않고 바로 적어도 DB 가 막는다")
	void dbRejectsRequestIdOnARecommendationRowEvenBypassingTheJavaGuard() {
		// 🔴 OutboxAppendCommand 생성자의 자바 검사(RECOMMENDATION_AGGREGATE_TYPE.equals(...)
		//    && requestId != null 이면 예외)를 일부러 거치지 않는다 — 배치 백필이나 다른 경로가
		//    이 표에 직접 쓰는 상황을 흉내낸다. ck_event_outbox_request_id_excludes_recommendation
		//    (V20260914070000)가 있어야 이 INSERT 가 막힌다.
		this.transactionTemplate.executeWithoutResult((status) -> {
			assertThatThrownBy(() -> this.jdbcTemplate.update("""
					INSERT INTO event_outbox
					  (event_id, event_type, event_version, aggregate_type, aggregate_id, request_id,
					   partition_key, payload, occurred_at, received_at)
					VALUES (?, 'recommendation_requested', 1, 'recommendation', ?, ?, ?, '{}'::jsonb, now(), now())
					""", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID().toString()))
					.isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
			status.setRollbackOnly();
		});
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
				requestId, requestId.toString(), Map.of("contact_email", "someone@example.com"), now, null, null, null,
				Producer.SERVER);

		assertThatThrownBy(() -> this.recorder.record(job, List.of(candidate), List.of(poisoned)))
				.isInstanceOf(RuntimeException.class);

		assertThat(this.jobRepository.findByRequestId(requestId)).isEmpty();
		assertThat(this.candidateRepository.countByRequestId(requestId)).isZero();
		assertThat(this.outboxRepository.count()).isZero();
	}

	private OutboxAppendCommand event(UUID eventId, UUID requestId) {
		return new OutboxAppendCommand(eventId, RecommendationCodes.EVENT_RECOMMENDATION_REQUESTED, 1,
				RecommendationCodes.AGGREGATE_TYPE, requestId, requestId.toString(), Map.of("event_kind", "test"),
				OffsetDateTime.now(), null, null, null, Producer.SERVER);
	}

	/**
	 * 행동 개인화를 켜 둔 계정 하나 — S15P21E201-549.
	 *
	 * <p>🔴 {@code EXPLICIT_ONLY} 로 만들면 이 사람의 행동 이벤트는 적히지 않는다. 그것이 정상
	 * 동작이고, 그 쪽을 재는 것은 {@code EventIngestServiceTest} 다. 여기서는 적히는 경로를
	 * 재므로 켜 둔 상태가 필요하다.
	 */
	private UUID behaviorEnabledUser() {
		UUID userId = UUID.randomUUID();
		this.jdbcTemplate.update("""
				INSERT INTO app_user
				  (user_id, display_name, language, personalization_mode, status, created_at, updated_at)
				VALUES (?, '이벤트검사', 'ko', 'BEHAVIOR_ENABLED', 'ACTIVE', now(), now())
				""", userId);
		return userId;
	}
}
