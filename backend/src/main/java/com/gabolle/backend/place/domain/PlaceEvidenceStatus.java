package com.gabolle.backend.place.domain;

/**
 * 장소 피처 하나를 우리가 얼마나 아는가. {@code place_feature.evidence_status} 의 세 값이다.
 *
 * <p>{@code trip.domain.TripConstraint.EvidenceStatus} 와 다른 목록이다. 그쪽은 다섯 값이고 제약
 * 판정에 쓴다 — 섞어 쓰지 않는다. 이름을 달리 둔 것도 같은 이름이면 import 한 줄로 조용히 섞이기
 * 때문이다.
 *
 * <p>{@link #UNKNOWN} 은 "값이 false 다" 가 아니라 "모른다" 다. DB 도 그렇게 강제한다
 * ({@code ck_place_feature_unknown_has_no_value} — UNKNOWN 이면 value 가 반드시 비어 있다).
 * 조회할 때 이 둘을 뭉개면 확인 안 된 장소가 안전한 것처럼 보인다.
 */
public enum PlaceEvidenceStatus {

	/** 확인했다. {@code value} 가 {@code false} 나 빈 배열이면 그것은 "해당 없음" 이다. */
	VERIFIED,

	/** 추정했다. 값은 있지만 원천이 확인해 준 것은 아니다. */
	ESTIMATED,

	/** 모른다. 값이 없다. 안전하다는 뜻이 아니다. */
	UNKNOWN
}
