package com.gabolle.backend.place.api;

import java.util.List;

/**
 * 근처 조회 응답.
 *
 * <p>{@code radiusExpanded} 가 참이면 {@code requestedRadiusM} 보다 {@code effectiveRadiusM} 이
 * 크다. 조용히 반경을 넓히지 않기 위한 칸이다 — 사용자는 "근처" 라고 믿는데 훨씬 먼 결과가 섞이는
 * 것을 막는다.
 *
 * <p>{@code scanTruncated} 는 경계상자 후보가 {@code nearbyMaxScanned} 를 넘어 일부만 거리를 쟀다는
 * 뜻이다. 오류는 아니지만 반경 안의 진짜 후보를 놓쳤을 수 있다는 신호다.
 *
 * <p>{@code facetKeyApplied} 는 요청의 {@code facetKey} 로 여덟 갈래 표식을 좁혔는지를 말한다.
 *
 * <p>{@code purposeApplied} 는 요청에 {@code purpose} 가 있어서 목적 필터(카테고리·표식)가 실제로
 * 적용됐는지를 말한다. {@code purpose} 를 안 보내면 {@code false} 이고, 그때는 반경 안의 모든
 * 장소가 거리순으로 나온다.
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
