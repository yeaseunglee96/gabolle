package com.gabolle.backend.place.loader;

/**
 * 넣을 영업시간 한 줄. {@code value} 는 {@code place_feature.value} 에 그대로 들어가는 JSON 이고
 * 정규화기가 낸 것에서 버리는 것이 없다 — "입장 마감 16:30" 을 떼어내면 16:45 에 도착하는 일정이
 * 만들어지고, 버린 뒤에는 되찾을 수 없다.
 */
public record OpeningHoursRow(String contentId, String featureType, String value) {
}
