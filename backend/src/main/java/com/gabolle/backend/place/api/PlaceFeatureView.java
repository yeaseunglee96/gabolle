package com.gabolle.backend.place.api;

import java.time.OffsetDateTime;

import tools.jackson.databind.JsonNode;

/**
 * 장소 상세 응답에 실리는 사실 하나.
 *
 * <h2>🔴 {@code evidenceStatus} 의 네 값이 이 티켓의 전부다</h2>
 *
 * S15P21E201-476 의 완료 기준이 <b>"정보 없음과 해당 없음이 응답에서 구분된다"</b> 이다. 두 값으로는
 * 못 나눈다. 네 가지를 구분한다.
 *
 * <ul>
 * <li>{@code VERIFIED} + {@code value:false} — <b>해당 없음.</b> 확인했고, 그 결과가 "아니다" 다.
 *     휠체어 접근이 안 되는 것이 확인된 장소가 여기다</li>
 * <li>{@code VERIFIED} + 값 있음 — 확인했고 그 값이다</li>
 * <li>{@code ESTIMATED} — 추정했다. 값은 있지만 원천이 확인해 준 것은 아니다</li>
 * <li>{@code UNKNOWN} — <b>정보 없음.</b> 이 사실을 보러 갔는데 못 정했다. {@code value} 는 반드시
 *     비어 있다 (DB CHECK {@code ck_place_feature_unknown_has_no_value} 가 강제한다)</li>
 * <li>{@code NOT_COLLECTED} — <b>아직 수집조차 안 됐다.</b> 표에 행이 없다. DB 에 없는 값이라
 *     응답 계층이 만들어 붙인다</li>
 * </ul>
 *
 * <p>{@code UNKNOWN} 과 {@code NOT_COLLECTED} 를 굳이 나누는 이유는 다음 행동이 다르기 때문이다.
 * 앞의 것은 수집을 시도했고 답이 없는 것이라 사람이 가서 봐야 하고, 뒤의 것은 수집 대상에 아직
 * 안 들어간 것이라 파이프라인을 손대야 한다.
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

	/** 표에 행이 없는 종류. DB 가 모르는 값이므로 여기서 만들어 붙인다. */
	public static PlaceFeatureView notCollected(String featureType) {
		return new PlaceFeatureView(featureType, null, "NOT_COLLECTED", null, null, null);
	}
}
