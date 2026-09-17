package com.gabolle.backend.story.presentation.dto;

/** 팔로워·팔로잉·차단 목록의 사람 한 명. */
public record RelationItemResponse(String userId, String displayName, String avatarUrl) {
}
