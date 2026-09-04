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
 */
public record NearbyPlaceResponse(
		List<NearbyPlaceItem> items,
		int requestedRadiusM,
		int effectiveRadiusM,
		boolean radiusExpanded,
		int expansionSteps,
		boolean scanTruncated,
		int limit) {
}
