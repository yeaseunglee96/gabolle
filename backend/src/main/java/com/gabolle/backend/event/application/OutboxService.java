package com.gabolle.backend.event.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.common.json.JsonPayloads;
import com.gabolle.backend.common.privacy.SensitivePayloadGuard;
import com.gabolle.backend.event.domain.EventOutbox;
import com.gabolle.backend.event.domain.EventType;
import com.gabolle.backend.event.repository.EventOutboxRepository;

/**
 * 이벤트를 Outbox 에 적는 유일한 입구. 다른 도메인은 {@code EventOutboxRepository} 를 직접
 * 부르지 않는다 — envelope 규칙·개인정보 검사·<b>동의 판정</b>·멱등이 여기 한 군데에만 있어야 한다.
 *
 * <p>여기 메서드는 스스로 트랜잭션을 열지 않는다({@code MANDATORY}). 부르는 쪽의 업무
 * 트랜잭션 안에서만 돌아야 업무 저장과 이벤트 저장이 같이 남거나 같이 롤백된다.
 *
 * <p>🔴 <b>동의 판정이 여기 있는 이유</b> (S15P21E201-1096). 전에는 행동 개인화를 끈 사람의
 * 이벤트를 안 적는 규칙이 {@link EventIngestService} <i>안에만</i> 있었고, 이 클래스를 직접
 * 부르는 경로는 그 검사를 지나지 않았다. 그 경로가 부르는 쪽에서 스스로 물어보는 것으로
 * 막고 있었는데, 그것은 <b>그때 있던 경로 하나를 막을 뿐이고 다음 사람은 또 모른다.</b>
 * 개인정보 검사는 이미 입구에 있었다 — 동의 검사만 그 원칙에서 빠져 있었다.
 */
@Service
@Profile({ "db", "dev" })
public class OutboxService {

	private final EventOutboxRepository repository;

	private final JsonPayloads jsonPayloads;

	private final SensitivePayloadGuard sensitivePayloadGuard;

	/**
	 * 🔴 {@code ObjectProvider} 인 것은 이 빈 없이 뜨는 좁은 시험 컨텍스트가 있어서다. 그리고
	 * 없을 때는 <b>행동 신호를 안 적는다</b> — 동의를 확인할 수 없는데 적는 것보다 안 적는 쪽이
	 * 맞다. 운영 기록은 그대로 적는다(동의와 무관한 값이다).
	 */
	private final ObjectProvider<BehaviorConsent> behaviorConsent;

	private final Clock clock;

	public OutboxService(EventOutboxRepository repository, JsonPayloads jsonPayloads,
			SensitivePayloadGuard sensitivePayloadGuard, ObjectProvider<BehaviorConsent> behaviorConsent,
			Clock clock) {
		this.repository = repository;
		this.jsonPayloads = jsonPayloads;
		this.sensitivePayloadGuard = sensitivePayloadGuard;
		this.behaviorConsent = behaviorConsent;
		this.clock = clock;
	}

	/** 이 이벤트가 어떻게 됐는가. 셋 다 오류가 아니고, 셋을 뭉치면 왜 안 쌓이는지 조사할 수 없다. */
	public enum Outcome {

		/** 새로 적혔다. */
		STORED,

		/** 이미 같은 {@code eventId} 가 있었다. 재전송이고, 오류가 아니다. */
		DUPLICATE,

		/** 일부러 안 적었다 — 이 사람이 행동 기반 개인화를 껐고 이 이벤트는 행동 관찰이다. */
		NOT_COLLECTED
	}

	/**
	 * @param event 적힌 행. {@link Outcome#NOT_COLLECTED} 면 비어 있다 — 「안 적었다」를
	 *     {@code null} 로 돌려주면 부르는 쪽이 그것을 실수로 읽고 터진다
	 * @param outcome 무슨 일이 있었나
	 */
	public record AppendResult(Optional<EventOutbox> event, Outcome outcome) {

		public static AppendResult stored(EventOutbox event) {
			return new AppendResult(Optional.of(event), Outcome.STORED);
		}

		public static AppendResult duplicate(EventOutbox event) {
			return new AppendResult(Optional.of(event), Outcome.DUPLICATE);
		}

		public static AppendResult notCollected() {
			return new AppendResult(Optional.empty(), Outcome.NOT_COLLECTED);
		}

		/** 이번 호출이 새로 적었는가. 「안 적었다」와 「이미 있었다」는 둘 다 {@code false} 다. */
		public boolean created() {
			return this.outcome == Outcome.STORED;
		}
	}

	/**
	 * 같은 {@code eventId} 로 두 번 불러도 행은 하나다. 두 번째 호출은 이미 있는 행을 돌려준다.
	 *
	 * <p>존재 확인과 저장 사이의 경합은 {@code event_id} 가 PK 라서 DB 가 막는다 — 확인을
	 * 통과한 두 번째 삽입은 제약 위반으로 실패하고, 그 트랜잭션만 롤백된다.
	 *
	 * @return 적힌 행. 동의가 없어 <b>일부러 안 적은</b> 경우는 비어 있다 — 그때도 예외가 아니다
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public Optional<EventOutbox> append(OutboxAppendCommand command) {
		return appendReportingDuplicate(command).event();
	}

	/**
	 * {@link #append} 와 같되 무슨 일이 있었는지까지 알려준다. 수집 API 가 계측 누락과 재전송과
	 * 「일부러 안 적음」을 구분할 수 있어야 해서 있다.
	 *
	 * <p>{@code STORED} 는 같은 트랜잭션 안에서만 정확하다. 다른 트랜잭션 둘이 같은
	 * {@code eventId} 로 동시에 들어오면 둘 다 그것을 볼 수 있고 하나는 커밋할 때 PK
	 * 제약으로 실패한다. 이 설계가 지키는 것은 행이 둘 생기지 않는 것이지 이 값의 정확도가 아니다.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public AppendResult appendReportingDuplicate(OutboxAppendCommand command) {
		this.sensitivePayloadGuard.verify(command.payload(), command.eventType() + ".payload");

		if (!collects(command)) {
			// 🔴 예외를 던지지 않는다. 이 호출은 대개 업무 저장과 같은 트랜잭션 안이고
			//    (Job·후보를 넣는 그 트랜잭션), 이벤트 하나가 「일부러 안 적힘」이 됐다고
			//    나머지까지 굴러떨어지면 안 된다. 거르고 계속 간다.
			return AppendResult.notCollected();
		}

		Optional<EventOutbox> existing = this.repository.findById(command.eventId());
		if (existing.isPresent()) {
			return AppendResult.duplicate(existing.get());
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
		return AppendResult.stored(this.repository.save(event));
	}

	/**
	 * 이 명령을 적어도 되는가. 행동 신호가 아니면 언제나 적는다 — 운영 기록
	 * ({@code recommendation_requested}·{@code recommendation_failed})을 끊으면 개인화를 끈
	 * 사람의 장애를 조사할 수 없다.
	 *
	 * <p>🔴 <b>모르는 종류는 조용히 통과시키지 않는다.</b> {@code eventType} 은 문자열이라
	 * 열거값으로 되돌려야 행동 신호인지 알 수 있는데, 못 되돌리는 값을 「행동 신호가 아닌가
	 * 보다」 로 넘기면 이 검사가 있으나 마나다. 그래서 던진다 — 이것은 거름이 아니라 <b>프로그램
	 * 오류</b>이고, 위의 「트랜잭션을 실패시키지 않는다」는 거름에 대한 약속이지 오류에 대한
	 * 약속이 아니다. 지금 명령을 만드는 자리는 모두 {@code EventType} 에서 이름을 얻는다.
	 */
	private boolean collects(OutboxAppendCommand command) {
		if (!EventType.fromWireName(command.eventType()).isBehaviorSignal()) {
			return true;
		}
		BehaviorConsent consent = this.behaviorConsent.getIfAvailable();
		return consent != null && consent.collects(command.userId());
	}
}
