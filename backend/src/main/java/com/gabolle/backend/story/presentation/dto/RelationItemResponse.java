package com.gabolle.backend.story.presentation.dto;

/**
 * 팔로워·팔로잉·차단 목록의 사람 한 명.
 *
 * @param following 보는 사람이 이 사람을 팔로우하는가. 목록 주인이 아니라 보는 사람 기준이다
 * @param storyCount 이 사람이 쓴 기록 중 보는 사람에게 보이는 것의 수. {@code null} 은 0 이
 *                  아니라 안 셌다는 뜻이다 — 차단 목록은 이 숫자를 그리지 않아 세지 않는다
 */
public record RelationItemResponse(String userId, String displayName, String avatarUrl, boolean following,
		Long storyCount) {
}
