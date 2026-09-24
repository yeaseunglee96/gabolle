package com.gabolle.backend.event.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.event.application.port.EventPublisherPort;
import com.gabolle.backend.event.config.OutboxRelayProperties;
import com.gabolle.backend.event.domain.EventOutbox;
import com.gabolle.backend.event.repository.EventOutboxRepository;

/**
 * 아직 안 보낸 이벤트를 골라 내보내는 릴레이.
 *
 * <p>리포지토리를 직접 부른다. {@code OutboxService} 는 적는 입구라서 거치지 않는다 —
 * 여기서 하는 일은 이미 적힌 것의 발행 상태를 갱신하는 것이다.
 *
 * <p>🔴 이 클래스는 <b>브로커가 무엇인지 모른다.</b> 카프카든 무엇이든
 * {@link EventPublisherPort} 뒤에 있다. 그래서 설정도 카프카 것이 아니라
 * {@link OutboxRelayProperties} 를 받는다 — 브로커를 갈아 끼울 때 재시도 상한과 배치 크기가
 * 같이 따라 사라지면 안 된다.
 */
@Service
@Profile({ "db", "dev" })
public class OutboxRelayService {

	private static final Logger log = LoggerFactory.getLogger(OutboxRelayService.class);

	private final EventOutboxRepository repository;

	private final EventPublisherPort publisher;

	private final OutboxRelayProperties properties;

	private final Clock clock;

	public OutboxRelayService(EventOutboxRepository repository, EventPublisherPort publisher,
			OutboxRelayProperties properties, Clock clock) {
		this.repository = repository;
		this.publisher = publisher;
		this.properties = properties;
		this.clock = clock;
	}

	/**
	 * 한 차례 돌린다. 외부 전송이 안에 있어 트랜잭션을 걸지 않는다 — 묶으면 중계 서버가 느릴 때
	 * DB 연결을 몇 초씩 붙잡는다. 대신 건별로 {@link #publishOne} 안에서만 짧게 커밋한다.
	 *
	 * @return 이번에 보낸 건수
	 */
	public int relayOnce() {
		if (!this.publisher.isAvailable()) {
			// 중계 서버가 죽었으면 오류를 던지지 않고 조용히 물러난다. 워커가 죽으면 다음
			// 차례가 안 오고, 그러면 쌓인 것이 영영 안 나간다.
			return 0;
		}

		warnAboutExhausted();

		// 🔴 재시도 상한을 넘긴 행은 조회에서 빠진다. 안 빼면 영원히 실패하는 한 건이 아래
		//    break 에 걸려 그 뒤의 모든 이벤트를 영구히 막는다(독약 메시지).
		List<EventOutbox> pending = this.repository.findByPublishedAtIsNullAndPublishAttemptsLessThanOrderBySeqAsc(
				this.properties.getMaxAttempts(), PageRequest.of(0, this.properties.getBatchSize()));
		int sent = 0;

		for (EventOutbox event : pending) {
			if (publishOne(event)) {
				sent++;
			}
			else {
				// 한 건이 실패하면 거기서 멈춘다. 건너뛰고 진행하면 순서가 뒤바뀌고, 실패
				// 원인이 공통일 때 나머지도 실패 카운트만 올린다.
				break;
			}
		}
		return sent;
	}

	/**
	 * 한도를 넘겨 더는 안 보내는 행이 있으면 매 차례 경고한다 (S15P21E201-1613).
	 *
	 * <p>🔴 전에는 조회에서 조용히 빠질 뿐이라, 표를 직접 열어 보지 않으면 이벤트가 안 나간 것을 아무도
	 * 몰랐다. 행은 지우지 않으므로 원인을 고친 뒤 {@code publish_attempts} 를 0 으로 되돌리면 다시 나간다.
	 */
	private void warnAboutExhausted() {
		int maxAttempts = this.properties.getMaxAttempts();
		long exhausted = this.repository.countByPublishedAtIsNullAndPublishAttemptsGreaterThanEqual(maxAttempts);
		if (exhausted > 0) {
			log.warn("event=OUTBOX_EXHAUSTED count={} maxAttempts={} — 한도를 넘겨 더는 안 보내는 이벤트가 있다. "
					+ "원인을 고친 뒤 publish_attempts 를 0 으로 되돌리면 다시 나간다", exhausted, maxAttempts);
		}
	}

	/**
	 * 한 건 보내고 결과를 적는다.
	 *
	 * <p>전송과 기록이 다른 시스템이라 "보냈는데 보냈다고 적기 전에" 죽는 구간은 없앨 수 없다.
	 * 그래서 이 릴레이는 "정확히 한 번" 이 아니라 "적어도 한 번" 을 보장하고, 받는 쪽이
	 * {@code eventId} 로 중복을 걸러야 한다 — {@link EventPublisherPort#publish} 의 계약이다.
	 */
	@Transactional
	public boolean publishOne(EventOutbox event) {
		if (!event.isPending()) {
			return true;
		}
		try {
			this.publisher.publish(event);
			event.markPublished(OffsetDateTime.now(this.clock));
			this.repository.save(event);
			return true;
		}
		catch (EventPublisherPort.BrokerUnavailableException unreachable) {
			// 🔴 이 이벤트 탓이 아니다 — 시도 횟수를 세지 않는다(S15P21E201-1613). 세면 브로커가
			//    몇 분 죽어 있는 동안 맨 앞 이벤트가 한도를 채워 영영 안 나간다. 행은 그대로 두고
			//    이번 차례만 멈춘다 — 브로커가 살아나면 다음 차례에 맨 앞부터 나간다.
			log.warn("event=OUTBOX_RELAY_BROKER_UNREACHABLE eventId={} reason={}", event.getEventId(),
					unreachable.getMessage());
			return false;
		}
		catch (RuntimeException ex) {
			event.markFailed(ex.getClass().getSimpleName() + ": " + ex.getMessage());
			this.repository.save(event);
			return false;
		}
	}
}
