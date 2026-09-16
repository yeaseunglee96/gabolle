package com.gabolle.backend.trip.presentation.dto;

import com.gabolle.backend.trip.domain.Trip;

/**
 * 여행 이름 바꾸기 요청 — {@code PUT /api/v1/trips/{tripId}/title} · S15P21E201-1023.
 *
 * @param title 붙일 이름. 🔴 <b>비었거나 공백뿐이면 이름을 지운다.</b> 지우기 전용 경로를
 *     따로 두지 않는 이유는 {@link Trip#rename(String, java.time.Instant)} 에 있다 —
 *     「빈 이름」과 「이름 없음」은 화면에서 구분할 수 없는 같은 것이라, 둘을 가르면 화면이
 *     두 가지를 다 검사해야 하고 언젠가 한쪽을 빠뜨린다
 */
public record UpdateTripTitleRequest(String title) {
}
