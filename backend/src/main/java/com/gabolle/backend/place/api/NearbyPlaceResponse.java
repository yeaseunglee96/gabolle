package com.gabolle.backend.place.api;

import java.util.List;

/**
 * 근처 조회 응답 (S15P21E201-469).
 *
 * <p>🔴 {@code radiusExpanded} 가 참이면 {@code requestedRadiusM} 보다 {@code effectiveRadiusM} 이
 * 크다. 조용히 반경을 넓히지 않는다는 완료 기준을 지키는 자리 — 사용자는 "근처" 라고 믿는데
 * 실제로는 훨씬 먼 결과가 섞이는 것을 막는다.
 *
 * <p>{@code scanTruncated} 는 경계상자 후보가 {@code nearbyMaxScanned} 를 넘어 일부만 자바에서
 * 거리를 재고 나머지는 안 쟀다는 뜻이다. 오류는 아니지만, 반경 안의 진짜 후보를 놓쳤을 수 있다는
 * 신호다.
 *
 * <p>{@code facetKeyApplied} 는 요청에 {@code facetKey} 가 와서 <b>여덟 갈래 표식</b>으로 좁혔는지를
 * 말한다 (S15P21E201-469 · -473). 화면의 "근처 기념품샵" 이 이 길로 온다 — {@code purpose} 설정이
 * 비어 있어 그 길로는 아무것도 좁힐 수 없었기 때문이다({@code purposeApplied} 설명 참고).
 *
 * <p>🔴 {@code purposeApplied} 는 요청에 {@code purpose} 가 있어서 목적 필터(카테고리·표식)가
 * 실제로 적용됐는지를 말한다. {@code purpose} 를 안 보내면 이 값은 {@code false} 이고, 그때는
 * 반경 안의 모든 장소가 거리순으로 나온다 — {@code purposes} 설정이 비어 있어도 이 엔드포인트가
 * 항상 400 을 내던 결함(-462 리뷰)을 고치면서 생긴 필드다.
 */
public record NearbyPlaceResponse(
		List<NearbyPlaceItem> items,
		int requestedRadiusM,
		int effectiveRadiusM,
		boolean radiusExpanded,
		int expansionSteps,
		boolean scanTruncated,
		int limit,
		boolean purposeApplied,
		boolean facetKeyApplied) {
}
