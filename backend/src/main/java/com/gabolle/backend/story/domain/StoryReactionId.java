package com.gabolle.backend.story.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * {@code (story_id, user_id)} — 한 사람이 한 글에 갖는 반응은 하나다.
 *
 * <p>대리키를 두면 「같은 사람이 같은 글에 둘」이 표 모양으로 가능해지고, 그걸 막는 UNIQUE 를 또 걸어야
 * 한다. 그러면 같은 규칙이 두 군데 생긴다. 키 자체가 그 규칙이면 어긋날 자리가 없다.
 */
@Embeddable
public class StoryReactionId implements Serializable {

	private static final long serialVersionUID = 1L;

	@Column(name = "story_id", nullable = false, updatable = false)
	private UUID storyId;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	protected StoryReactionId() {
		// JPA 전용
	}

	public StoryReactionId(UUID storyId, UUID userId) {
		this.storyId = Objects.requireNonNull(storyId, "storyId 는 필수다");
		this.userId = Objects.requireNonNull(userId, "userId 는 필수다");
	}

	public UUID getStoryId() {
		return this.storyId;
	}

	public UUID getUserId() {
		return this.userId;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof StoryReactionId that)) {
			return false;
		}
		return Objects.equals(this.storyId, that.storyId) && Objects.equals(this.userId, that.userId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.storyId, this.userId);
	}
}
