package com.gabolle.backend.itinerary.domain;

import java.util.List;
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

    /**
     * 🔴 일정을 처음 만든다. version=1 · operation=CREATE.
     *
     * <p>V20260903150000 마이그레이션 주석이 "그 경로가 생기는 티켓이 최초 값을 명시적으로
     * 넣어야 한다"고 남긴 자리다 — S15P21E201-604 가 그 티켓이다. {@code itinerary}
     * 하나와 그 첫 판을 함께 만든다({@code itineraries}·{@code itinerary_versions} 가
     * 둘 다 채워져야 "판은 있는데 포인터가 없는" 상태가 생기지 않는다).
     *
     * @param firstVersion {@code version() == 1}·{@code operation() == CREATE} 여야 한다
     */
    Itinerary create(Itinerary itinerary, ItineraryVersion firstVersion);

    /**
     * 이 판의 항목·구간을 저장한다. {@link #create} 또는 {@link #append} 와
     * <b>같은 트랜잭션</b> 안에서 불려야 한다 — 판은 저장됐는데 내용이 없는 상태를
     * 만들지 않기 위해서다.
     */
    void saveContent(String itineraryVersionId, List<ItineraryItem> items, List<ItineraryLeg> legs);

    /** 판 하나의 전체 내용(판 + 항목 + 구간). 판이 없으면 비어 있다. */
    Optional<ItineraryContent> findContent(String itineraryId, int version);
}
