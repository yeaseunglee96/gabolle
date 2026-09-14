package com.gabolle.backend.place.loader;

/**
 * 넣을 영업시간 한 줄 — S15P21E201-852.
 *
 * @param contentId  관광공사 식별자. 장소 id 와 {@code source_id} 를 여기서 만든다
 * @param featureType {@code OPENING_HOURS} 또는 숙박의 {@code CHECK_IN_OUT}
 * @param value      {@code place_feature.value} 에 그대로 들어가는 JSON 문자열.
 *     🔴 정규화기가 낸 것에서 <b>버리는 것이 없다</b> — 계절 분기·부가 조건·원문을 함께 담는다.
 *     "입장 마감 16:30" 을 떼어내면 16:45 에 도착하는 일정이 만들어지고, 버린 뒤에는 되찾을 수 없다
 */
public record OpeningHoursRow(String contentId, String featureType, String value) {
}
