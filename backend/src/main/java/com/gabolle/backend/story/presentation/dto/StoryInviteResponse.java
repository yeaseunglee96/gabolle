package com.gabolle.backend.story.presentation.dto;

/**
 * {@code acceptPath} 는 앱의 딥링크 주소가 아니라 서버 API 경로다 — 서버는 앱이 이 표를 어떤 화면
 * 주소로 감쌀지 모른다.
 */
public record StoryInviteResponse(
		String inviteId,
		String storyId,
		String token,
		/** ISO-8601. 발급 시각 + 7일 — 여행 초대({@code TripInvite.TTL})와 같은 값이다. */
		String expiresAt,
		String acceptPath) {
}
