package com.gabolle.backend.auth.api;

/**
 * 계정 삭제 전 안내 화면이 보여줄 실제 영향 수 — S15P21E201-188/195(진미리 님 요청).
 *
 * <p>🔴 여기 숫자는 {@link com.gabolle.backend.auth.service.AccountDeletionService#delete}가
 * <b>실제로 지우는 범위</b>와 같은 기준으로 센다 — 화면에 보여주고 실제로는 안 지워지는 것이
 * 있으면 그게 더 나쁘다.
 *
 * <p>🔴 {@code reviewCount}는 없다. 이 백엔드에 리뷰 도메인 자체가 아직 없다 — 없는 것을
 * 0으로 지어내면 "지금은 없다"와 "0개 있는데 다 지워진다"를 화면이 구분할 수 없다.
 */
public record AccountDeletionPreviewResponse(
		long ownedTripCount,
		long itineraryCount,
		long recordCount) {
}
