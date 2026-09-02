package com.gabolle.backend.event.application;

import com.gabolle.backend.event.application.port.EventPublisherPort;
import com.gabolle.backend.event.domain.EventOutboxRepository;
import com.gabolle.backend.event.domain.OutboxEvent;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 아직 안 보낸 이벤트를 골라 내보내는 릴레이 — S15P21E201-354.
 *
 * <p>완료 기준이 셋이고 전부 <b>실패를 견디는 것</b>에 관한 것이다.
 * <ul>
 *   <li>중계 서버를 껐다 켜면 그 사이 쌓인 이벤트가 전달된다</li>
 *   <li>🔴 <b>두 번 돌려도 같은 이벤트가 두 번 기록되지 않는다</b></li>
 *   <li>중계 서버가 안 되는 동안 워커가 오류로 죽지 않는다</li>
 * </ul>
 */
@Service
public class OutboxRelayService {

    /** 한 번에 몇 건씩. 너무 크면 한 건 실패에 전체가 늦어진다. */
    private static final int BATCH_SIZE = 100;

    private final EventOutboxRepository repository;
    private final EventPublisherPort publisher;
    private final Clock clock;

    public OutboxRelayService(EventOutboxRepository repository,
                              EventPublisherPort publisher,
                              Clock clock) {
        this.repository = repository;
        this.publisher = publisher;
        this.clock = clock;
    }

    /**
     * 한 차례 돌린다.
     *
     * <p>🔴 <b>트랜잭션을 걸지 않는다.</b> 외부 전송이 안에 들어 있어서,
     * 트랜잭션으로 묶으면 Kafka 가 느릴 때 DB 연결을 몇 초씩 붙잡는다.
     * 대신 건별로 {@link #publishOne} 을 부르고 그 안에서만 짧게 커밋한다.
     *
     * @return 이번에 보낸 건수
     */
    public int relayOnce() {
        if (!publisher.isAvailable()) {
            // 🔴 중계 서버가 죽었으면 조용히 물러난다. 오류를 던지지 않는다 —
            //    워커가 죽으면 다음 차례가 안 오고, 그러면 쌓인 것이 영영 안 나간다.
            return 0;
        }

        List<OutboxEvent> pending = repository.findPending(BATCH_SIZE);
        int sent = 0;

        for (OutboxEvent event : pending) {
            if (publishOne(event)) {
                sent++;
            } else {
                // 🔴 한 건이 실패하면 거기서 멈춘다. 뒤 것을 건너뛰고 진행하면
                //    순서가 뒤바뀌고, 실패 원인이 공통(중계 서버 다운)일 때
                //    나머지 99건도 전부 실패 카운트만 올린다.
                break;
            }
        }
        return sent;
    }

    /**
     * 한 건 보내고 결과를 적는다.
     *
     * <p>🔴 <b>"보냈는데 보냈다고 적기 전에" 죽는 구간이 남는다.</b>
     * 이건 없앨 수 없다 — 전송과 기록이 서로 다른 시스템이기 때문이다.
     * 그래서 다시 돌면 같은 것을 또 보내게 되고, <b>받는 쪽이 {@code eventId} 로
     * 중복을 걸러야 한다.</b> 그게 {@link EventPublisherPort#publish} 의 계약이다.
     *
     * <p>즉 이 릴레이는 "정확히 한 번" 이 아니라 <b>"적어도 한 번"</b> 을 보장하고,
     * 멱등성으로 그 차이를 메운다.
     */
    @Transactional
    public boolean publishOne(OutboxEvent event) {
        if (!event.isPending()) {
            return true;
        }
        try {
            publisher.publish(event);
            event.markPublished(clock.instant());
            repository.save(event);
            return true;
        } catch (RuntimeException e) {
            // 🔴 실패한 것을 표에서 지우지 않는다. 다음 차례에 다시 시도한다.
            event.markFailed(e.getClass().getSimpleName() + ": " + e.getMessage());
            repository.save(event);
            return false;
        }
    }
}
