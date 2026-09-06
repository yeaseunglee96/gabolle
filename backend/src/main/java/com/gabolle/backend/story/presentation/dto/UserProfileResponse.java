package com.gabolle.backend.story.presentation.dto;

/**
 * 프로필 머리 — 이름·팔로워·팔로잉·공개 기록 수·내가 팔로우하는지. S15P21E201-126.
 *
 * <p>로컬 등급은 아직 없다(방문 인증 `-279`·`-287` 뒤에 생긴다). 그 칸은 그때 끝에 붙인다.
 *
 * @param storyCount 요청자가 볼 수 있는 기록 수. 본인이면 전부, 팔로워면 PUBLIC·FOLLOWERS, 아니면 PUBLIC 만
 */
public record UserProfileResponse(
		String userId,
		String displayName,
		long followerCount,
		long followingCount,
		long storyCount,
		boolean following,
		boolean me) {
}
