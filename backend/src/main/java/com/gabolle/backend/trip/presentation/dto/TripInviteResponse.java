package com.gabolle.backend.trip.presentation.dto;

/**
 * 초대 발급 응답. {@code acceptPath} 는 앱의 딥링크 주소가 아니라 서버 API 경로다 — 앱이 그 표를
 * 어떤 화면 주소로 감쌀지는 서버가 모른다.
 */
public record TripInviteResponse(
		String inviteId,
		String tripId,
		String role,
		String token,
		/** ISO-8601. 발급 시각 + 7일. */
		String expiresAt,
		String acceptPath) {
}
