package com.gabolle.backend.story.presentation.dto;

/**
 * 프로필 머리 — 이름·팔로워·팔로잉·공개 기록 수·내가 팔로우하는지.
 *
 * @param storyCount 요청자가 볼 수 있는 기록 수. 본인이면 전부, 팔로워면 PUBLIC·FOLLOWERS, 아니면 PUBLIC 만
 * @param avatarUrl 프로필 사진 주소. {@code null} 이면 안 골랐다는 뜻이고 화면은 기본 그림을 그린다
 * @param coverUrl 배경 사진(프로필 맨 위에 깔리는 큰 사진) 주소. {@code null} 이면 안 골랐거나 이 사람이 나를
 *                 차단한 것이고, 화면은 기본 사진을 깐다
 * @param blocked 내가 이 사람을 차단했나. 버튼이 「차단하기」인지 「차단 해제」인지를 이 값이 정한다
 * @param blockedByUser 이 사람이 나를 차단했나. {@link #blocked} 와 서로 다른 값이다 — A 가 B 를 차단해도
 *                      B 는 A 를 차단하지 않은 상태일 수 있다
 */
public record UserProfileResponse(
		String userId,
		String displayName,
		long followerCount,
		long followingCount,
		long storyCount,
		boolean following,
		boolean me,
		String avatarUrl,
		String coverUrl,
		boolean blocked,
		boolean blockedByUser) {
}
