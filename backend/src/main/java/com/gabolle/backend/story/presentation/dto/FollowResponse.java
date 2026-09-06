package com.gabolle.backend.story.presentation.dto;

/** 팔로우·해제 뒤의 상태. 두 번 눌러도 같은 모양이 온다 — 멱등. */
public record FollowResponse(String userId, boolean following, long followerCount, long followingCount) {
}
