package com.gabolle.backend.itinerary.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 일정 저장소 인터페이스. 구현(JPA)은 infra 계층에 있고 이 파일은 JPA·Spring 을 import 하지 않는다.
 * 판과 내용(항목·구간·제외 목록)은 항상 함께 저장한다 — 항목·구간·제외의 부모가
 * itineraryVersionId 라서 판마다 내용을 복사해야 하고, 저장 경로를 나누면 내용이 빈 판이
 * 최신이 될 수 있다.
 * 최신 판 포인터를 옮기는 것은 구현체의 책임이다. 응용 계층은 Itinerary.moveTo 를 부르지 않는다.
 */
public interface ItineraryRepository {

    Optional<Itinerary> findById(String itineraryId);

    /**
     * 판 하나와 그 판의 내용을 한 트랜잭션으로 더한다.
     * 같은 {@code (itineraryId, version)} 이 이미 있으면 {@link StaleItineraryVersionException} 을
     * 던진다 — DB 의 UNIQUE 제약 위반을 그 예외로 바꾸는 것이 구현체의 책임이다.
     * 최신 판 포인터는 {@code baseVersion} 에서만 움직인다. 그 사이 다른 게시가 있었으면 역시
     * 같은 예외다 — 판 번호 UNIQUE 는 번호를 지키는 제약이지 포인터를 지키는 제약이 아니다.
     * items·legs·exclusions 는 바뀌지 않은 것까지 새 판의 전부를 받는다. 판은 덮어쓰지 않는 스냅샷이다.
     */
    ItineraryVersion appendVersion(ItineraryVersion version,
                                   List<ItineraryItem> items,
                                   List<ItineraryLeg> legs,
                                   List<ItineraryExclusion> exclusions);

    Optional<ItineraryVersion> findVersion(String itineraryId, int version);

    /**
     * 일정을 처음 만든다. version=1 · operation=CREATE.
     * 일정과 첫 판과 첫 판의 내용을 함께 만든다 — "판은 있는데 내용이 없는" 상태가 생기지 않게.
     *
     * @param firstVersion {@code version() == 1}·{@code operation() == CREATE} 여야 한다
     */
    Itinerary create(Itinerary itinerary, ItineraryVersion firstVersion,
                     List<ItineraryItem> items, List<ItineraryLeg> legs);

    /** 판 하나의 전체 내용(판 + 항목 + 구간 + 제외 목록). 판이 없으면 비어 있다. */
    Optional<ItineraryContent> findContent(String itineraryId, int version);

    /**
     * 한 일정의 판 목록, 최신 판이 먼저(version DESC). 쪽을 나눠 받는다 — 판은 고칠 때마다
     * 쌓여서 끝이 없다. 정렬이 version DESC 로 완전히 정해져 있어(같은 일정에 같은 판 번호가
     * 둘일 수 없다) 쪽을 나눠도 같은 판이 두 번 나오거나 조용히 건너뛰어지지 않는다.
     *
     * @param page 0부터
     * @return 없는 일정이면 빈 쪽
     */
    VersionPage findVersions(String itineraryId, int page, int size);

    /**
     * 판 목록 한 쪽.
     *
     * @param hasMore 더 있는데 안 보냈다. 이 칸이 없으면 부르는 쪽이 "상한에 걸린 것"과
     *     "마침 그만큼 있는 것"을 구분할 수 없다
     */
    record VersionPage(List<ItineraryVersion> versions, boolean hasMore) {
    }

    /**
     * 한 여행의 일정 전부. 지금은 여행마다 일정이 하나지만 표는 여럿을 허용한다.
     */
    List<Itinerary> findByTripId(String tripId);

    /**
     * 여러 일정의 판을 만든 시각이 늦은 것부터 {@code limit} 개.
     * 판 자체가 이력이라 따로 이력 표를 두지 않는다.
     */
    List<ItineraryVersion> findRecentVersions(Collection<String> itineraryIds, int limit);

    /**
     * 이 일정을 방금 골랐다 — 여행의 「지금 확정된 일정」이 된다(S15P21E201-1602, {@code itineraries.chosen_at}).
     * 일정이 새로 생길 때는 DB 기본값이 그 시각을 채우므로 부르지 않아도 된다. 이미 있는 안을 다시 고를 때 부른다.
     * 메모리 구현과 시험 대역은 확정 개념이 없어 아무것도 안 한다.
     */
    default void markChosen(String itineraryId, Instant at) {
    }
}
