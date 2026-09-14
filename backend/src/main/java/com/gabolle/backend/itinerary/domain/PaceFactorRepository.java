package com.gabolle.backend.itinerary.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/**
 * 개인 속도 계수 저장소 — S15P21E201-304. {@link ItineraryItemActualRepository} 와 같은
 * 이유로 <b>인터페이스만</b> 도메인 계층에 두고 JPA·Spring 을 import 하지 않는다.
 *
 * <p>계수는 판 체인이라 {@link ItineraryRepository} 처럼 "판을 더한다" 는 동작 하나로
 * 갱신을 표현한다 — 행을 찾아 고치는 메서드를 따로 두지 않는다. 고치는 메서드가 있으면
 * 언젠가 그것으로 판을 덮어써 버리는 실수가 생기고, 그러면 이 계수 자체가 있는 이유
 * (과거 계산이 어느 판으로 나왔는지 되짚기)가 사라진다.
 */
public interface PaceFactorRepository {

    /**
     * 지금 쓰는 판. 한 사람에게 최대 하나임을 표의 조건부 UNIQUE 색인
     * ({@code uq_user_pace_factor_current})이 보장하므로 {@code Optional} 이 맞다.
     *
     * @return 아직 계수를 구한 적 없으면 빈 값 — {@code 1.0} 을 대신 답하지 않는다
     */
    Optional<PaceFactor> findCurrent(String userId);

    /**
     * 앞 판에 supersededAt 을 찍고 새 판을 넣는다. 반드시 한 트랜잭션이어야 한다.
     *
     * <p>둘을 나눠서 실행하면 그 사이에 다른 요청이 끼어들 여지가 생기고, 표의 조건부
     * UNIQUE 색인이 그중 하나를 거부한다 — 계수는 있는데 "지금 판"이 없거나 둘이 되는
     * 상태를 만들지 않으려고 이 계약을 인터페이스에 명시한다.
     *
     * <p>version 채번은 이 메서드를 구현하는 쪽의 몫이다 — 앞 판이 있으면 그 version + 1,
     * 없으면 1.
     *
     * @return 새로 만들어진 판
     */
    PaceFactor appendVersion(String userId, BigDecimal factor, int sampleCount, Instant observedUntil, Instant now);
}
