package com.gabolle.backend.event.application;

import java.time.Clock;
import java.time.OffsetDateTime;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.common.json.JsonPayloads;
import com.gabolle.backend.common.privacy.SensitivePayloadGuard;
import com.gabolle.backend.event.domain.EventOutbox;
import com.gabolle.backend.event.repository.EventOutboxRepository;

/**
 * 이벤트를 Outbox 에 적는 유일한 입구.
 *
 * <p>🔴 다른 도메인은 {@code EventOutboxRepository} 를 직접 부르지 않는다. envelope 규칙
 * (**모든 이벤트가 공통으로 가져야 하는 머리 부분 — event_id · type · version · 시각**)과
 * 개인정보 검사와 멱등 처리가 여기 한 군데에만 있어야 하기 때문이다.
 *
 * <p>🔴 이 메서드는 <b>스스로 트랜잭션을 열지 않는다</b>({@code MANDATORY}). 부르는 쪽의
 * 업무 트랜잭션 안에서만 돌아야 Outbox 의 존재 이유 — 업무 저장과 이벤트 저장이 같이 남거나
 * 같이 롤백되는 것 — 이 성립한다. 트랜잭션 밖에서 부르면 그 자리에서 예외가 난다.
 */
@Service
@Profile({ "db", "dev" })
public class OutboxService {

	private final EventOutboxRepository repository;

	private final JsonPayloads jsonPayloads;

	private final SensitivePayloadGuard sensitivePayloadGuard;

	private final Clock clock;

	public OutboxService(EventOutboxRepository repository, JsonPayloads jsonPayloads,
			SensitivePayloadGuard sensitivePayloadGuard, Clock clock) {
		this.repository = repository;
		this.jsonPayloads = jsonPayloads;
		this.sensitivePayloadGuard = sensitivePayloadGuard;
		this.clock = clock;
	}

	/**
	 * 같은 {@code eventId} 로 두 번 불러도 행은 하나다. 두 번째 호출은 이미 있는 행을 돌려준다.
	 *
	 * <p>존재 확인과 저장 사이의 경합은 {@code event_id} 가 PK 라서 DB 가 막는다 — 확인을
	 * 통과한 두 번째 삽입은 제약 위반으로 실패하고, 그 트랜잭션만 롤백된다.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public EventOutbox append(OutboxAppendCommand command) {
		this.sensitivePayloadGuard.verify(command.payload(), command.eventType() + ".payload");

		return this.repository.findById(command.eventId()).orElseGet(() -> {
			OffsetDateTime receivedAt = OffsetDateTime.now(this.clock);
			EventOutbox event = new EventOutbox(
					command.eventId(),
					command.eventType(),
					command.eventVersion(),
					command.aggregateType(),
					command.aggregateId(),
					command.partitionKey(),
					this.jsonPayloads.writeObject(command.payload()),
					command.occurredAt(),
					receivedAt);
			return this.repository.save(event);
		});
	}
}
