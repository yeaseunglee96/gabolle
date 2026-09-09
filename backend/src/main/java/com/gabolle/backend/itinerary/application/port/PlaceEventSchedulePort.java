package com.gabolle.backend.itinerary.application.port;

import java.time.LocalDate;

/**
 * 일정 → 장소 방향의 유일한 문 — S15P21E201-467.
 *
 * <p>일정 계층은 장소의 저장소({@code place.repository})를 직접 부르지 않는다(백엔드 README
 * "다른 기능의 Repository 를 직접 호출하지 않습니다"). 대신 <b>쓰는 쪽인 일정이 이 인터페이스를
 * 정의하고 제공하는 쪽인 장소가 구현한다</b> — 반대 방향의 선례가 이미 있다
 * ({@code recommendation.application.port.ItineraryDraftPort} 를 {@code itinerary} 가 구현한다).
 *
 * <h2>🔴 장소의 타입을 노출하지 않는다</h2>
 * 주고받는 것이 {@code String}·{@link LocalDate}·{@code boolean} 뿐이다. 여기에
 * {@code PlaceEventPeriod}(JPA 엔티티)를 실으면 일정이 장소의 표 구조에 묶이고, 그 엔티티를
 * 고칠 때마다 일정이 함께 깨진다. 축제 회차가 몇 개인지·어느 공고에서 왔는지는 일정이 알 필요가
 * 없다 — 일정이 묻는 것은 "그 날 문을 여느냐" 하나다.
 */
public interface PlaceEventSchedulePort {

    /**
     * {@code from}~{@code to} 사이에 이 장소가 문을 여는 날들.
     *
     * <p>기간이 정해진 장소가 아니면 {@link PlaceEventSchedule#unscheduled()} 를 돌려준다.
     * "기간 행이 없다" 와 "기간 행은 있는데 이 구간에는 하나도 안 걸린다" 는 호출자에게 서로
     * 다른 판단으로 이어지므로 둘을 빈 목록으로 뭉개지 않는다
     * ({@link PlaceEventSchedule} 주석 참고).
     *
     * @param placeId 장소 식별자. 일정 계층은 이것을 문자열로 들고 다닌다
     * @param from 여행 시작일 (포함)
     * @param to 여행 마지막 날 (포함)
     */
    PlaceEventSchedule scheduleWithin(String placeId, LocalDate from, LocalDate to);
}
