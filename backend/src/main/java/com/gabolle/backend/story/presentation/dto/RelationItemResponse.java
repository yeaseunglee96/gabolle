package com.gabolle.backend.story.presentation.dto;

/**
 * 팔로워·팔로잉·차단 목록의 사람 한 명.
 *
 * @param following 지금 보는 사람(로그인한 사람)이 이 사람을 팔로우하는가 — S15P21E201-1179.
 *                  목록을 소유한 사람(팔로워·팔로잉 목록의 주인) 기준이 아니라 <b>보는 사람</b>
 *                  기준이다. 화면의 팔로우 버튼이 이 값으로 초기 상태를 그린다.
 */
public record RelationItemResponse(String userId, String displayName, String avatarUrl, boolean following) {
}
