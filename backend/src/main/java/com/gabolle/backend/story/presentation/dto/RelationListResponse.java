package com.gabolle.backend.story.presentation.dto;

import java.util.List;

/** 팔로워·팔로잉·차단 목록 한 묶음. {@code nextCursor} 가 {@code null} 이면 더 없다. */
public record RelationListResponse(List<RelationItemResponse> items, String nextCursor) {
}
