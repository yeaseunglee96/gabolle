package com.gabolle.backend.trip.presentation.dto;

import com.gabolle.backend.trip.domain.Trip;

/**
 * 여행 이름 바꾸기 요청 — {@code PUT /api/v1/trips/{tripId}/title}.
 *
 * @param title 붙일 이름. 비었거나 공백뿐이면 이름을 지운다 — 지우기 전용 경로는 따로 없다.
 *     빈 이름과 이름 없음은 화면에서 구분할 수 없는 같은 것이라 둘을 가르지 않는다
 *     ({@link Trip#rename(String, java.time.Instant)})
 */
public record UpdateTripTitleRequest(String title) {
}
