package com.gabolle.backend.place.loader;

import java.time.LocalDate;

/**
 * 수집본에서 읽은 축제 회차 한 건. {@code contentId} 는 관광공사 식별자이고 장소 id 를 이 값에서
 * 계산한다({@link TourApiPlaceLoader#placeIdOf}) — 장소 적재가 같은 규칙을 쓰므로 이을 단계가
 * 따로 없다.
 */
public record FestivalPeriodRow(String contentId, String title, LocalDate startDate, LocalDate endDate) {
}
