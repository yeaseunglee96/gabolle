package com.gabolle.backend.story.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 사용자가 저장한(북마크) 기록. 좋아요·싫어요와 다른 표라 한 글에 반응과 저장을 동시에 가질 수 있다.
 * {@code SavedPlace} 와 같은 모양으로, 여행과 무관한 전역 목록이고 그 사람만의 것이다.
 */
@Entity
@Table(name = "story_save")
public class StorySave {

	@Id
	@Column(name = "story_save_id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(name = "story_id", nullable = false, updatable = false)
	private UUID storyId;

	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	protected StorySave() {
		// JPA 전용
	}

	private StorySave(UUID id, UUID userId, UUID storyId, OffsetDateTime createdAt) {
		this.id = id;
		this.userId = userId;
		this.storyId = storyId;
		this.createdAt = createdAt;
	}

	public static StorySave of(UUID id, UUID userId, UUID storyId, OffsetDateTime createdAt) {
		return new StorySave(id, userId, storyId, createdAt);
	}

	public UUID getId() {
		return this.id;
	}

	public UUID getUserId() {
		return this.userId;
	}

	public UUID getStoryId() {
		return this.storyId;
	}

	public OffsetDateTime getCreatedAt() {
		return this.createdAt;
	}
}
