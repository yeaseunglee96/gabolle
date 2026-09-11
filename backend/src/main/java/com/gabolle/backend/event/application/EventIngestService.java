package com.gabolle.backend.event.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.event.domain.EventType;
import com.gabolle.backend.event.domain.Producer;
import com.gabolle.backend.user.domain.PersonalizationMode;
import com.gabolle.backend.user.repository.AppUserRepository;

/**
 * 이벤트 적재 — 응용 계층 (S15P21E201-352).
 *
 * <p>🔴 이 서비스의 완료 기준은 <b>"중계 서버가 꺼져 있어도 정상 응답한다"</b> 다.
 * 그러려면 여기서 Kafka 를 부르지 않아야 한다. 적기만 하고 끝낸다.
 *
 * <h2>🔴 2026-09-03 — 이 클래스가 메모리에 적고 있었다</h2>
 * 같은 {@code event} 패키지 안에 <b>이름이 같은 리포지토리 인터페이스가 둘</b> 있었다.
 * <ul>
 *   <li>{@code event.domain.EventOutboxRepository} (S15P21E201-352) — 구현이 메모리 하나뿐</li>
 *   <li>{@code event.repository.EventOutboxRepository} (S15P21E201-543) — JPA, DB 로 감</li>
 * </ul>
 * 이 서비스가 앞의 것을 물고 있어서 <b>POST /api/v1/events 로 받은 이벤트가 DB 가 아니라
 * 메모리에 쌓였고, 서버를 끄면 사라졌다.</b> 표는 멀쩡해 보이는데 안이 비는 종류의 고장이라
 * 아무도 몰랐다 — 고지혁 님이 S15P21E201-554 표를 만들다 실측으로 찾았다.
 *
 * <p>메모리 구현을 지우고 {@link OutboxService} 하나로 합쳤다. 그래야 개인정보 검사
 * ({@code SensitivePayloadGuard}) 와 envelope 규칙과 멱등 처리가 한 군데에만 있다.
 *
 * <h2>🔴 2026-09-03 — request_id·user_id·trip_id·producer 를 payload 대신 실컬럼으로</h2>
 * 고지혁 님이 S15P21E201-352-event-outbox-join-axes 에서 {@code event_outbox} 에
 * 네 컬럼을 추가했다. 그 전까지 이 서비스는 그 네 값을 payload(JSONB) 안에 문자열로
 * 욱여넣고 있었다({@code withEnvelopeFields}, 이제 지웠다) — S15P21E201-542 14장의
 * "후보 → 노출을 request_id + place_id 로 조인" 을 못 쓰는 원인이었다. 이제 칸으로 간다.
 *
 * <h2>🔴 no-db 프로필에는 이 빈이 없다</h2>
 * {@code application.properties} 가 정한 팀 규칙 그대로다 — "DB 를 쓰는 추천·Outbox 빈은
 * 인증과 같은 방식으로 {@code @Profile({"db","dev"})} 가 가른다".
 * DB 없이 띄우면 {@code /api/v1/events} 는 <b>아예 없다.</b> 있는 척하면서 메모리에 적는 것보다
 * 없는 편이 낫다 — 있는 척하는 쪽이 방금 그 고장이었다.
 *
 * <h2>탈퇴 익명화 (NFR-08) — 2026-09-11 정정</h2>
 * 여기 <i>"아직 어느 탈퇴 흐름도 이걸 부르지 않는다"</i> 고 적혀 있었다. <b>사실이 아니다.</b>
 * {@code AccountDeletionService.detachEvents} 가 부른다 — S15P21E201-425 가 들어올 때 붙었는데
 * 이 문단만 안 고쳐졌다. 낡은 실측을 지우지 않고 정정한 날짜와 함께 남긴다(팀 규칙 1절).
 *
 * <p>탈퇴 정책은 그대로 "계정은 삭제, 이벤트는 익명화" 다. 지우면 과거 추천 평가를 재현할 수 없다.
 *
 * <h2>🔴 행동 기반 개인화를 끈 사람 (S15P21E201-549)</h2>
 * 탈퇴와 <b>다르게</b> 다룬다. 껐으면 행동 관찰 이벤트({@link EventType#isBehaviorSignal()})를
 * <b>아예 적지 않는다</b> — 익명화로는 부족하기 때문이다. 이유는
 * {@link #collectsBehaviorOf(java.util.UUID)} 에 있다.
 *
 * <p>🔴 이 표에는 {@code user_id} 외래키를 걸지 않는다. FK 가 있으면 CASCADE 로 이벤트가 같이
 * 지워지거나 RESTRICT 로 계정 삭제가 막힌다 — 둘 다 정책 위반이다.
 */
@Service
@Profile({ "db", "dev" })
public class EventIngestService {

	/**
	 * 🔴 envelope 축으로 쓰는 실컬럼 이름. payload에는 중복 저장하지 않는다.
	 *
	 * <p>이 값들은 {@code event_outbox} 실컬럼으로 저장한다. payload 안에 같은 키를 허용하면
	 * 컬럼과 JSON 값이 서로 달라질 수 있으므로 입구에서 거부한다.
	 */
	static final Set<String> ENVELOPE_KEYS = Set.of("request_id", "user_id", "trip_id", "producer");

	private final OutboxService outboxService;

	private final AppUserRepository users;

	private final Clock clock;

	public EventIngestService(OutboxService outboxService, AppUserRepository users, Clock clock) {
		this.outboxService = outboxService;
		this.users = users;
		this.clock = clock;
	}

	/**
	 * 이 이벤트가 어떻게 됐는가 — S15P21E201-549.
	 *
	 * <p>🔴 {@code boolean} 이었다. 상태가 셋이 되면서 열거형으로 바꿨다 — 껐다는 이유로
	 * 안 적힌 것과 이미 있어서 안 적힌 것은 <b>같은 false 가 아니다.</b> 하나로 뭉치면
	 * "왜 안 쌓이지" 를 조사할 때 둘을 구분할 방법이 없다.
	 */
	public enum Outcome {

		/** 새로 적혔다. */
		STORED,

		/** 이미 같은 {@code eventId} 가 있었다. 재전송이고, 오류가 아니다. */
		DUPLICATE,

		/**
		 * 🔴 <b>일부러 안 적었다.</b> 이 사람이 행동 기반 개인화를 껐고 이 이벤트는 행동 관찰이다.
		 * 오류가 아니므로 앱은 화면에 아무것도 띄우지 않는다.
		 */
		NOT_COLLECTED
	}

	/**
	 * 클라이언트가 보낸 이벤트를 적는다.
	 *
	 * <p>🔴 이미 받은 {@code eventId} 면 <b>조용히 성공으로 응답한다.</b>
	 * 재전송은 오류가 아니다 — 앱이 400 을 받으면 사용자에게 오류를 띄운다.
	 */
	@Transactional
	public Outcome ingestFromClient(UUID eventId, EventType type, int eventVersion, UUID userId, UUID tripId,
			UUID requestId, OffsetDateTime occurredAt, Map<String, Object> payload) {

		return append(eventId, type, eventVersion, Producer.CLIENT, userId, tripId, requestId, occurredAt, payload);
	}

	/**
	 * 서버 비즈니스 로직이 이벤트를 적는다 — <b>Outbox 패턴의 핵심</b> (DR-05).
	 *
	 * <p>🔴 {@code @Transactional(propagation = REQUIRED)} 가 기본값이므로, 부르는 쪽
	 * (예: 좋아요 저장)이 이미 트랜잭션 안이면 <b>그 트랜잭션에 합류한다.</b> 그래서
	 * "좋아요는 저장됐는데 이벤트는 없는" 상태가 생기지 않는다.
	 *
	 * <p>🔴 <b>여기서 새 트랜잭션을 열면(REQUIRES_NEW) Outbox 패턴이 깨진다.</b>
	 * 비즈니스 저장이 롤백돼도 이벤트만 남는다.
	 */
	@Transactional
	public void recordFromServer(UUID eventId, EventType type, int eventVersion, UUID userId, UUID tripId,
			UUID requestId, Map<String, Object> payload) {

		OffsetDateTime now = OffsetDateTime.now(this.clock);
		append(eventId, type, eventVersion, Producer.SERVER, userId, tripId, requestId, now, payload);
	}

	/**
	 * 이 사람의 행동을 지금 적어도 되는가 — S15P21E201-549.
	 *
	 * <h2>🔴 왜 {@code user_id} 만 비우지 않고 아예 안 적는가</h2>
	 *
	 * 탈퇴({@code AccountDeletionService.detachEvents})는 사람만 떼고 사건은 남긴다. 여기서
	 * 같은 방법을 쓸 수 없다. 행동 이벤트는 대부분 축이 여행({@code AggregateAxis.TRIP})이라
	 * {@code aggregate_id} 에 {@code trip_id} 가 들어 있고, 그 여행에는 주인이 있다.
	 * <b>{@code user_id} 를 비워도 여행을 거쳐 그 사람으로 되돌아갈 수 있다.</b>
	 * 탈퇴는 여행 자체가 함께 지워져서 그 길이 끊기지만, 개인화를 끈 사람의 여행은 남는다.
	 *
	 * <p>되돌릴 수 있는 가리기는 가린 것이 아니다. 그래서 적지 않는다.
	 *
	 * <h2>🔴 없는 사람과 모르는 사람을 다르게 다룬다</h2>
	 *
	 * <ul>
	 * <li>{@code userId == null} — <b>적는다.</b> 사람이 안 붙은 이벤트는 이미 익명이라
	 *     여기서 막아도 지켜지는 개인정보가 없고, 집계만 사라진다. 지금 HTTP 경로는
	 *     인증을 요구하므로 이 값이 {@code null} 로 오는 것은 서버가 스스로 적는 자리다</li>
	 * <li>사람은 있는데 <b>계정을 못 찾는다</b> — <b>안 적는다.</b> 실재하는 사람의 ID 인데
	 *     동의를 확인할 수 없는 상태다. 그때는 모으지 않는 쪽이 맞다 — 실패는 조용한
	 *     수집이 아니라 빈 자리로 나타나야 한다</li>
	 * </ul>
	 */
	private boolean collectsBehaviorOf(UUID userId) {
		if (userId == null) {
			return true;
		}
		return this.users.findPersonalizationMode(userId)
				.filter(PersonalizationMode.BEHAVIOR_ENABLED::equals)
				.isPresent();
	}

	private Outcome append(UUID eventId, EventType type, int eventVersion, Producer producer, UUID userId, UUID tripId,
			UUID requestId, OffsetDateTime occurredAt, Map<String, Object> payload) {

		if (eventId == null) {
			throw new IllegalArgumentException("eventId 는 멱등 키다. 비울 수 없다");
		}
		if (!type.allowsProducer(producer)) {
			// DR-13 — 생산 책임이 뒤바뀌면 신뢰할 수 없는 값이 들어온다.
			throw new IllegalArgumentException(
					type + " 은 " + type.expectedProducer() + " 가 만들어야 한다 (받은 값: " + producer + ")");
		}
		if (occurredAt == null) {
			throw new IllegalArgumentException("occurredAt 은 필수다: " + type);
		}

		OffsetDateTime receivedAt = OffsetDateTime.now(this.clock);
		if (occurredAt.isAfter(receivedAt)) {
			// 발생이 수신보다 뒤일 수는 없다. 기기 시계가 틀렸거나 조작된 값이다.
			throw new IllegalArgumentException("occurredAt(" + occurredAt + ") 이 수신 시각(" + receivedAt + ") 보다 뒤다");
		}

		UUID aggregateId = aggregateIdOf(type, userId, tripId, requestId);

		OutboxAppendCommand command = new OutboxAppendCommand(
				eventId,
				type.wireName(),
				eventVersion,
				type.aggregateType(),
				aggregateId,
				partitionKeyOf(userId, aggregateId),
				withoutEnvelopeFields(payload),
				occurredAt,
				requestIdColumnOf(type, requestId),
				userId,
				tripId,
				producer);

		// 🔴 형식 검사를 <b>전부 지난 뒤에</b> 동의를 본다 (S15P21E201-549). 명령을 다 만들고
		//    나서 보는 이유가 그것이다 — 축 판정({@code aggregateIdOf})과 envelope 검사
		//    ({@code withoutEnvelopeFields})도 형식 검사이고, 그 앞에서 끊으면 개인화를 끈
		//    사람이 보낸 잘못된 이벤트가 202 를 받는다. 그러면 앱의 계측 버그가 "그 사람만
		//    안 쌓인다" 로 보이고, 원인을 개인화에서 찾게 된다.
		//
		//    🔴 검사만 하고 <b>적지는 않는다.</b> 명령을 만드는 것은 부작용이 없다.
		if (type.isBehaviorSignal() && !collectsBehaviorOf(userId)) {
			return Outcome.NOT_COLLECTED;
		}

		return this.outboxService.appendReportingDuplicate(command).created() ? Outcome.STORED : Outcome.DUPLICATE;
	}

	/**
	 * 이 이벤트가 붙는 대상의 ID.
	 *
	 * <p>축은 {@link EventType} 이 정한다 — 추천 요청이면 {@code request_id}, 여행이면
	 * {@code trip_id}, 사용자면 {@code user_id}. {@code aggregate_id} 컬럼이 {@code UUID NOT NULL}
	 * 이라 <b>축이 가리키는 값이 비어 있으면 적을 수 없다.</b> 임의로 다른 값을 넣지 않고
	 * 그 자리에서 거부한다.
	 *
	 * <p>🔴 <b>{@code requestId} 필수 검사가 여기 있는 이유</b> (2026-09-07, S15P21E201-735).
	 * 예전에는 이 메서드 바깥에서 <b>모든 이벤트</b>에 {@code requestId} 를 요구했다. 그런데
	 * 그 값을 앱에 알려주는 응답이 하나도 없어서, 앱이 보내는 저장·제외·방문이 전부 튕겼다.
	 * 필수인 진짜 이유는 API-07 이라는 이름이 아니라 <b>이 축의 {@code aggregate_id} 가 곧
	 * 그 값</b>이라는 구조다. 그래서 검사를 그 구조가 있는 자리로 옮겼다 — 추천 축에서는
	 * 여전히 필수이고, 다른 축에서는 있으면 {@code request_id} 칸으로 이어 붙이고 없으면 비운다.
	 */
	private UUID aggregateIdOf(EventType type, UUID userId, UUID tripId, UUID requestId) {
		// 🔴 축이 null 인 경우를 switch 안에서 다루지 않는다 — Java 17 에서 case null 은 아직
		//    프리뷰 기능이라 컴파일되지 않는다. 스위치에 들어가기 전에 거른다.
		if (!type.hasAggregateAxis()) {
			throw new IllegalStateException(type + " 의 aggregate 축이 아직 정해지지 않았다");
		}
		return switch (type.aggregateAxis()) {
			case RECOMMENDATION_REQUEST -> {
				if (requestId == null) {
					// API-07 — 이 축의 이벤트는 requestId 가 곧 aggregate_id 다. 없으면 노출과
					// 행동을 이을 수 없고, 컬럼이 NOT NULL 이라 애초에 적히지도 않는다.
					throw new IllegalArgumentException(
							type + " 은 추천 요청에 붙는 이벤트다. requestId 없이는 노출과 행동을 이을 수 없다 (API-07)");
				}
				yield requestId;
			}
			case TRIP -> {
				if (tripId == null) {
					throw new IllegalArgumentException(type + " 은 여행에 붙는 이벤트다. tripId 없이는 적을 수 없다");
				}
				yield tripId;
			}
			case USER -> {
				if (userId == null) {
					throw new IllegalArgumentException(type + " 은 사용자에 붙는 이벤트다. userId 없이는 적을 수 없다");
				}
				yield userId;
			}
		};
	}

	/**
	 * {@code event_outbox.request_id} 실컬럼에 넣을 값 — 축에 따라 다르다.
	 *
	 * <p>🔴 추천 이벤트는 요청 축이 이미 {@code aggregateId} 다. 여기서 또 채우면 같은 값이
	 * 두 칸에 있고, 나중에 하나만 고쳐지면 둘이 어긋난다(2026-09-03 결정,
	 * {@link OutboxAppendCommand} 가 이 규칙을 다시 한 번 강제한다). 그래서 추천 이벤트는
	 * {@code null} 을 보낸다 — requestId 파라미터 자체는 여전히 필수다(API-07, 위 검사),
	 * 다만 그 값이 <b>가는 칸이</b> aggregate_id 냐 request_id 냐가 달라질 뿐이다.
	 */
	private UUID requestIdColumnOf(EventType type, UUID requestId) {
		return type.aggregateAxis() == EventType.AggregateAxis.RECOMMENDATION_REQUEST ? null : requestId;
	}

	/**
	 * 브로커로 보낼 때 순서를 지켜야 하는 단위.
	 *
	 * <p>기본은 사용자다 — 한 사람의 행동은 순서대로 읽혀야 한다. 🔴 비로그인 이벤트는
	 * 사용자가 없으므로 <b>aggregate 를 순서 단위로 쓴다.</b> 고정 문자열("anonymous")을 쓰면
	 * 비로그인 이벤트 전부가 한 칸에 몰려서, 나중에 브로커를 켰을 때 그 칸 하나가 병목이 된다.
	 */
	private String partitionKeyOf(UUID userId, UUID aggregateId) {
		return (userId != null ? userId : aggregateId).toString();
	}

	/** envelope 값이 JSON payload에 중복 저장되지 않도록 검사한다. */
	private Map<String, Object> withoutEnvelopeFields(Map<String, Object> payload) {
		if (payload != null) {
			for (String reserved : ENVELOPE_KEYS) {
				if (payload.containsKey(reserved)) {
					throw new IllegalArgumentException(
							"payload 에 envelope 키가 들어 있다: " + reserved + ". 이 값은 컬럼으로 받는다");
				}
			}
		}
		return payload;
	}
}
