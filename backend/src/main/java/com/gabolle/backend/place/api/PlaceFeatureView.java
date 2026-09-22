package com.gabolle.backend.place.api;

import java.time.OffsetDateTime;

import tools.jackson.databind.JsonNode;

/**
 * 장소 상세 응답에 실리는 사실 하나.
 *
 * <p>{@code evidenceStatus} 는 "정보 없음" 과 "해당 없음" 을 구분하려고 네 값을 쓴다.
 * {@code VERIFIED} 는 확인한 값이고, 그중 {@code value:false} 가 해당 없음이다(휠체어 접근이 안
 * 되는 것이 확인된 장소). {@code ESTIMATED} 는 값은 있지만 원천이 확인해 준 것은 아니다.
 * {@code UNKNOWN} 은 보러 갔는데 못 정한 것이고 {@code value} 는 반드시 비어 있다
 * (DB CHECK {@code ck_place_feature_unknown_has_no_value}). {@code NOT_COLLECTED} 는 표에 행이 아예
 * 없다는 뜻이라 DB 가 아니라 응답 계층이 만들어 붙인다.
 *
 * <p>{@code UNKNOWN} 과 {@code NOT_COLLECTED} 를 나누는 이유는 다음 행동이 다르기 때문이다. 앞의
 * 것은 사람이 가서 봐야 하고, 뒤의 것은 수집 파이프라인을 손대야 한다.
 *
 * @param value {@code UNKNOWN} 과 {@code NOT_COLLECTED} 에서는 항상 {@code null} 이다
 */
public record PlaceFeatureView(
		String featureType,
		String featureKey,
		String evidenceStatus,
		JsonNode value,
		OffsetDateTime observedAt,
		String sourceType) {

	public static PlaceFeatureView notCollected(String featureType) {
		return new PlaceFeatureView(featureType, null, "NOT_COLLECTED", null, null, null);
	}
}
