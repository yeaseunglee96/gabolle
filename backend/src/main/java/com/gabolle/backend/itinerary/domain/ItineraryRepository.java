package com.gabolle.backend.itinerary.domain;

import java.util.Optional;

/**
 * 일정 저장소 — <b>인터페이스만</b> 도메인 계층에 둔다.
 *
 * <p>🔴 구현(JPA)은 {@code infra} 계층에 있다. 이 파일은 JPA·Spring 을 import 하지 않는다.
 * 그래서 도메인 규칙을 DB 없이 테스트할 수 있다 — 가짜 구현을 끼우면 된다.
 *
 * <p>의존성 역전: {@code infra} 가 {@code domain} 을 향한다. 그 반대가 아니다.
 */
public interface ItineraryRepository {

    Optional<Itinerary> findById(String itineraryId);

    /**
     * 판을 저장한다.
     *
     * <p>🔴 같은 {@code (itineraryId, version)} 이 이미 있으면
     * {@link StaleItineraryVersionException} 을 던져야 한다.
     * DB 의 UNIQUE 제약 위반을 그 예외로 바꾸는 것이 구현체의 책임이다.
     */
    ItineraryVersion append(ItineraryVersion version);

    Optional<ItineraryVersion> findVersion(String itineraryId, int version);
}
