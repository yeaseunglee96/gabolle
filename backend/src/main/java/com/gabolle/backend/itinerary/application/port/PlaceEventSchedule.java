package com.gabolle.backend.itinerary.application.port;

import java.time.LocalDate;
import java.util.List;

/**
 * "이 장소는 여행 기간 중 어느 날 문을 여는가" 에 대한 대답 — S15P21E201-467.
 *
 * <h2>🔴 {@code scheduled} 와 빈 {@code openDates} 는 다른 사실이다</h2>
 * 두 칸으로 나눈 이유가 이것이다. 호출자는 세 가지 경우를 서로 다르게 다뤄야 한다.
 * <ul>
 *   <li>{@code scheduled=false} — 기간이라는 개념이 없는 장소다(식당·카페·해수욕장). 날짜
 *       검사를 걸 근거가 없으므로 <b>검사를 통째로 건너뛴다</b></li>
 *   <li>{@code scheduled=true}, {@code openDates} 가 빈 목록 — 회차는 있는데 이 여행 기간과
 *       하나도 겹치지 않는다. 이 여행에는 어느 날을 골라도 넣을 수 없다</li>
 *   <li>{@code scheduled=true}, {@code openDates} 에 날짜가 있음 — 그 날들만 넣을 수 있다</li>
 * </ul>
 * 뒤의 두 경우를 "빈 목록" 하나로 합치면 화면이 "다른 날을 고르면 되는가" 를 판정할 수 없다.
 *
 * @param scheduled 이 장소에 열리는 기간이 정해져 있는가
 * @param openDates 물어본 구간 안에서 실제로 문을 여는 날들. 오름차순이고 중복이 없다
 */
public record PlaceEventSchedule(boolean scheduled, List<LocalDate> openDates) {

    public PlaceEventSchedule {
        openDates = openDates == null ? List.of() : List.copyOf(openDates);
    }

    /** 기간이라는 개념이 없는 장소. 날짜 검사와 중복 검사를 둘 다 건너뛰라는 뜻이다. */
    public static PlaceEventSchedule unscheduled() {
        return new PlaceEventSchedule(false, List.of());
    }

    /** 기간이 정해진 장소. {@code openDates} 가 비어 있어도 {@code scheduled} 는 참이다. */
    public static PlaceEventSchedule openOn(List<LocalDate> openDates) {
        return new PlaceEventSchedule(true, openDates);
    }

    public boolean opensOn(LocalDate date) {
        return this.openDates.contains(date);
    }
}
