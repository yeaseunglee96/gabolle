package com.gabolle.backend.trip.presentation.dto;

/**
 * 초대 발급 응답 — S15P21E201-294.
 *
 * <p>{@code acceptPath} 는 앱의 딥링크 주소가 아니라 서버 API 경로다. 서버는 앱이 그 표를
 * 어떤 화면 주소로 감쌀지 모르므로 토큰과 이 경로만 준다.
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
