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
	 * 적은 결과.
	 *
	 * @param event 적힌 행 (새로 적혔든 이미 있었든)
	 * @param created 이번 호출이 새로 적었으면 {@code true}, 이미 있었으면 {@code false}
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
	 * {@link #append} 와 같되 <b>새로 적혔는지까지</b> 알려준다.
	 *
	 * <p>수집 API(POST /api/v1/events)가 응답에 {@code duplicate} 를 담아야 해서 있다.
	 * 재전송은 오류가 아니므로 둘 다 성공 응답이지만, 앱과 분석이 "받은 것이 새것인지"
	 * 를 알 수 있어야 계측 누락과 재전송을 구분할 수 있다.
	 *
	 * <p>🔴 이 판정은 <b>같은 트랜잭션 안에서만 정확하다.</b> 서로 다른 트랜잭션 둘이 같은
	 * {@code eventId} 로 동시에 들어오면 둘 다 {@code created = true} 를 볼 수 있고, 그중
	 * 하나는 커밋할 때 PK 제약으로 실패한다. <b>행이 둘 생기지는 않는다</b> — 그것이
	 * 이 설계가 지키는 것이고, 플래그의 정확도는 지키는 대상이 아니다.
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
