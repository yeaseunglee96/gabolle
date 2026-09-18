package com.gabolle.backend.story.presentation.dto;

/**
 * 프로필 머리 — 이름·팔로워·팔로잉·공개 기록 수·내가 팔로우하는지. S15P21E201-126.
 *
 * <p>로컬 등급은 아직 없다(방문 인증 `-279`·`-287` 뒤에 생긴다). 그 칸은 그때 끝에 붙인다.
 *
 * @param storyCount 요청자가 볼 수 있는 기록 수. 본인이면 전부, 팔로워면 PUBLIC·FOLLOWERS, 아니면 PUBLIC 만
 * @param avatarUrl 프로필 사진 주소. {@code null} 이면 안 골랐다는 뜻이고 화면은 기본 그림을 그린다
 *                  (S15P21E201-844). 로컬 등급과 달리 이 칸은 끝에 붙여도 되는 값이라 여기 뒀다
 * @param blocked 🔴 <b>내가 이 사람을 차단했나.</b> 버튼이 「차단하기」인지 「차단 해제」인지를 이 값이
 *                정한다 (S15P21E201-990)
 * @param blockedByUser 🔴 <b>이 사람이 나를 차단했나.</b> 화면은 이때 「차단되어 볼 수 없습니다」를 띄운다.
 *                      {@link #blocked} 와 <b>서로 다른 값</b>이다 — A 가 B 를 차단해도 B 는 A 를
 *                      차단하지 않은 상태일 수 있다. 하나로 합치면 그 경우를 못 가른다
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
		boolean blocked,
		boolean blockedByUser) {
}
