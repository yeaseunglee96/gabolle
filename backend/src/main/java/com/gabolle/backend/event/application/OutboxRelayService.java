package com.gabolle.backend.event.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.event.application.port.EventPublisherPort;
import com.gabolle.backend.event.domain.EventOutbox;
import com.gabolle.backend.event.repository.EventOutboxRepository;

/**
 * 아직 안 보낸 이벤트를 골라 내보내는 릴레이.
 *
 * <p>리포지토리를 직접 부른다. {@code OutboxService} 는 적는 입구라서 거치지 않는다 —
 * 여기서 하는 일은 이미 적힌 것의 발행 상태를 갱신하는 것이다.
 */
@Service
@Profile({ "db", "dev" })
public class OutboxRelayService {

	/** 한 번에 몇 건씩. 너무 크면 한 건 실패에 전체가 늦어진다. */
	private static final int BATCH_SIZE = 100;

	private final EventOutboxRepository repository;

	private final EventPublisherPort publisher;

	private final Clock clock;

	public OutboxRelayService(EventOutboxRepository repository, EventPublisherPort publisher, Clock clock) {
		this.repository = repository;
		this.publisher = publisher;
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

		List<EventOutbox> pending = this.repository
			.findByPublishedAtIsNullOrderBySeqAsc(PageRequest.of(0, BATCH_SIZE));
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
		catch (RuntimeException ex) {
			event.markFailed(ex.getClass().getSimpleName() + ": " + ex.getMessage());
			this.repository.save(event);
			return false;
		}
	}
}
