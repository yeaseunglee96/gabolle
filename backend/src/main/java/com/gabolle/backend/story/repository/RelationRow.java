package com.gabolle.backend.story.repository;

import java.time.Instant;
import java.util.UUID;

/**
 * 팔로워·팔로잉·차단 목록의 사람 한 줄 — {@link UserFollowRepository}·{@link UserBlockRepository} 가 같이 쓴다.
 *
 * <p>네이티브 질의의 결과 별칭({@code userId}·{@code displayName}·{@code avatarUrl}·{@code relatedAt})이
 * 이 인터페이스의 getter 이름과 그대로 맞아야 Spring Data 가 값을 채운다.
 */
public interface RelationRow {

	UUID getUserId();

	String getDisplayName();

	String getAvatarUrl();

	Instant getRelatedAt();
}
