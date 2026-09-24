package com.gabolle.backend.itinerary.application.port;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * 일정 → 장소 방향의 유일한 문.
 * 일정 계층은 장소의 저장소를 직접 부르지 않는다. 대신 쓰는 쪽인 일정이 이 인터페이스를
 * 정의하고 제공하는 쪽인 장소가 구현한다.
 * 장소의 타입을 노출하지 않는다 — 주고받는 것이 {@code String}·{@link LocalDate}·
 * {@code boolean} 뿐이다. 여기에 JPA 엔티티를 실으면 일정이 장소의 표 구조에 묶이고, 그
 * 엔티티를 고칠 때마다 일정이 함께 깨진다. 일정이 묻는 것은 "그 날 문을 여느냐" 하나다.
 */
public interface PlaceEventSchedulePort {

    /**
     * {@code from}~{@code to} 사이에 이 장소가 문을 여는 날들.
     * 기간이 정해진 장소가 아니면 {@link PlaceEventSchedule#unscheduled()} 를 돌려준다.
     * "기간 행이 없다" 와 "기간 행은 있는데 이 구간에는 하나도 안 걸린다" 는 호출자에게 서로 다른
     * 판단으로 이어지므로 둘을 빈 목록으로 뭉개지 않는다.
     *
     * @param placeId 장소 식별자. 일정 계층은 이것을 문자열로 들고 다닌다
     * @param from 여행 시작일 (포함)
     * @param to 여행 마지막 날 (포함)
     */
    PlaceEventSchedule scheduleWithin(String placeId, LocalDate from, LocalDate to);

    /**
     * 여러 장소를 한 번에 — {@link #scheduleWithin} 과 같은 판정이다. 추천 후보를 일정에 앉힐 때 쓴다.
     * 기본 구현은 하나씩 묻는다. 장소 쪽 구현은 한 번의 질의로 덮어쓴다.
     *
     * @return 물어본 장소마다 한 칸. 기간이 정해지지 않은 장소도 {@code unscheduled} 로 들어 있다
     */
    default Map<String, PlaceEventSchedule> schedulesWithin(Collection<String> placeIds, LocalDate from,
            LocalDate to) {
        Map<String, PlaceEventSchedule> out = new HashMap<>();
        for (String placeId : placeIds) {
            out.put(placeId, scheduleWithin(placeId, from, to));
        }
        return out;
    }
}
