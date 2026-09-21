package com.gabolle.backend.event.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.common.json.JsonPayloads;
import com.gabolle.backend.common.privacy.SensitivePayloadGuard;
import com.gabolle.backend.event.domain.EventOutbox;
import com.gabolle.backend.event.repository.EventOutboxRepository;

/**
 * 이벤트를 Outbox 에 적는 유일한 입구. 다른 도메인은 {@code EventOutboxRepository} 를 직접
 * 부르지 않는다 — envelope 규칙·개인정보 검사·멱등이 여기 한 군데에만 있어야 한다.
 *
 * <p>여기 메서드는 스스로 트랜잭션을 열지 않는다({@code MANDATORY}). 부르는 쪽의 업무
 * 트랜잭션 안에서만 돌아야 업무 저장과 이벤트 저장이 같이 남거나 같이 롤백된다.
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
	 * @param event 적힌 행 — 새로 적혔든 이미 있었든
	 * @param created 이번 호출이 새로 적었으면 {@code true}
	 */
	public record AppendResult(EventOutbox event, boolean created) {
	}

	/**
	 * 같은 {@code eventId} 로 두 번 불러도 행은 하나다. 두 번째 호출은 이미 있는 행을 돌려준다.
	 *
	 * <p>존재 확인과 저장 사이의 경합은 {@code event_id} 가 PK 라서 DB 가 막는다 — 확인을
	 * 통과한 두 번째 삽입은 제약 위반으로 실패하고, 그 트랜잭션만 롤백된다.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public EventOutbox append(OutboxAppendCommand command) {
		return appendReportingDuplicate(command).event();
	}

	/**
	 * {@link #append} 와 같되 새로 적혔는지까지 알려준다. 수집 API 가 계측 누락과 재전송을
	 * 구분할 수 있어야 해서 있다.
	 *
	 * <p>{@code created} 는 같은 트랜잭션 안에서만 정확하다. 다른 트랜잭션 둘이 같은
	 * {@code eventId} 로 동시에 들어오면 둘 다 {@code true} 를 볼 수 있고 하나는 커밋할 때 PK
	 * 제약으로 실패한다. 이 설계가 지키는 것은 행이 둘 생기지 않는 것이지 이 값의 정확도가 아니다.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public AppendResult appendReportingDuplicate(OutboxAppendCommand command) {
		this.sensitivePayloadGuard.verify(command.payload(), command.eventType() + ".payload");

		Optional<EventOutbox> existing = this.repository.findById(command.eventId());
		if (existing.isPresent()) {
			return new AppendResult(existing.get(), false);
		}

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
				receivedAt,
				command.requestId(),
				command.userId(),
				command.tripId(),
				command.producer());
		return new AppendResult(this.repository.save(event), true);
	}
}
