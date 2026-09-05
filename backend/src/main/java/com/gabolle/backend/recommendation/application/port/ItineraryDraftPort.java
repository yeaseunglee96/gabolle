package com.gabolle.backend.recommendation.application.port;

/**
 * 추천 결과를 실제 일정으로 바꾸는 경계 — S15P21E201-604.
 *
 * <p>🔴 {@code recommendation} 패키지는 {@code itinerary} 패키지를 직접 알지 못한다(양쪽이
 * 서로 다른 슬라이스로 테스트되고, {@code RecommendationSliceApplication} 이 itinerary 를
 * 스캔하지 않는다). 그래서 여기 인터페이스만 두고 구현({@code itinerary.application
 * .ItineraryDraftService})은 {@link org.springframework.beans.factory.ObjectProvider} 로
 * 주입한다 — {@code RecommendationService} 가 {@code RecommendationEnginePort} 에 대해
 * 이미 쓰고 있는 것과 같은 판단이다.
 */
public interface ItineraryDraftPort {

    /**
     * 🔴 순수 계산 + 읽기만. DB 쓰기를 하지 않는다. 트랜잭션 밖에서 불린다 — 여기서
     * 실패하면 기존 {@code abandon()} 경로를 타서 후보는 전부 남는다(추천 자체는 성공했다는
     * 사실이 지워지지 않는다).
     */
    ItineraryDraft assemble(ItineraryDraftCommand command);

    /** 🔴 바깥 트랜잭션 안에서 불린다. 구현에 {@code @Transactional} 을 달지 않는다. */
    ItineraryHandle persist(ItineraryDraft draft);
}
