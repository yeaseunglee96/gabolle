package com.gabolle.backend.auth.api;

/**
 * 계정 삭제 전 안내 화면이 보여줄 실제 영향 수. 숫자는
 * {@link com.gabolle.backend.auth.service.AccountDeletionService#delete} 가 실제로 지우는 범위와
 * 같은 기준으로 세야 한다.
 *
 * <p>{@code reviewCount} 는 없다. 없는 것을 0 으로 지어내면 "지금은 없다" 와 "0개 있는데 다
 * 지워진다" 를 화면이 구분할 수 없다.
 */
public record AccountDeletionPreviewResponse(
		long ownedTripCount,
		long itineraryCount,
		long recordCount) {
}
