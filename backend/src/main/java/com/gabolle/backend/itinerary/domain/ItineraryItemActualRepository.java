package com.gabolle.backend.itinerary.domain;

import java.util.List;

/**
 * 방문지 실제 시각 저장소 — S15P21E201-293. {@link ItineraryRepository} 와 같은 이유로
 * <b>인터페이스만</b> 도메인 계층에 두고 JPA·Spring 을 import 하지 않는다.
 *
 * <h2>왜 {@link ItineraryRepository} 에 메서드를 더하지 않았나</h2>
 * 그 저장소가 다루는 것은 <b>판</b>이다 — {@code appendVersion} 이 항목·구간·제외 목록을
 * 한 트랜잭션으로 새 판에 복사하는 것이 그 인터페이스의 전부다. 실제 시각은 그 판 체인
 * 밖에 있어서(판이 바뀌어도 같은 행) 같은 인터페이스에 넣으면 "이것도 판마다 복사되나" 를
 * 읽는 사람이 매번 확인해야 한다. 저장 단위가 다르면 저장소도 다르게 둔다.
 */
public interface ItineraryItemActualRepository {

    /**
     * 그 방문지의 기록을 <b>덮어쓴다</b> — 없으면 만들고 있으면 바꾼다.
     *
     * <p>🔴 {@code (itineraryId, itemKey)} 가 이 기록의 이름이므로 같은 방문지에 두 번
     * 보내면 행이 두 개 생기는 것이 아니라 마지막 값이 남는다({@code -293} 완료 기준).
     * 표의 {@code uq_itinerary_item_actual} 이 그 사실의 최종 보증이다.
     *
     * @return 저장된 뒤의 상태. 인자로 받은 것과 같다 — 서버가 값을 보정하지 않는다
     */
    ItineraryItemActual upsert(ItineraryItemActual actual);

    /**
     * 그 일정에 남은 실제 시각 전부. 조회 응답이 방문지마다 채워 넣는다.
     *
     * <p>항목 하나씩 묻지 않고 일정 단위로 한 번에 읽는다 — 방문지 수만큼 질의를 날리면
     * 일정 하나 그리는 데 질의가 수십 개가 된다({@code ix_itinerary_item_actual_itinerary}
     * 가 이 조회를 위해 있는 색인이다).
     *
     * @return 기록이 하나도 없으면 빈 목록
     */
    List<ItineraryItemActual> findByItineraryId(String itineraryId);
}
