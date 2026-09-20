package com.gabolle.backend.itinerary.application.port;

import java.time.LocalDate;
import java.util.List;

/**
 * "이 장소는 여행 기간 중 어느 날 문을 여는가" 에 대한 대답.
 * {@code scheduled} 와 빈 {@code openDates} 는 다른 사실이라 칸을 둘로 나눴다. 호출자는 세
 * 경우를 다르게 다뤄야 한다 — {@code scheduled=false} 면 기간이라는 개념이 없는 장소라
 * 날짜 검사를 통째로 건너뛰고, {@code scheduled=true} 인데 {@code openDates} 가 비면 회차는
 * 있는데 이 여행 기간과 하나도 안 겹쳐 어느 날을 골라도 못 넣고, 날짜가 있으면 그 날들만
 * 넣을 수 있다. 뒤의 둘을 빈 목록 하나로 합치면 화면이 "다른 날을 고르면 되는가" 를 판정할
 * 수 없다.
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
