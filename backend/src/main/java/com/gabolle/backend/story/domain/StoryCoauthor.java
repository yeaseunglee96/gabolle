package com.gabolle.backend.story.domain;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * 기록에 초대받아 함께 쓰게 된 사람 한 줄 — 만든 사람({@code story.author_user_id})은 여기 들어가지 않는다.
 *
 * <p>기본키가 (기록, 사람) 쌍이다. 같은 초대 링크를 두 번 눌러도 두 줄이 생길 수 없는 것은 코드가 아니라
 * 이 키가 보장한다.
 */
@Entity
@Table(name = "story_coauthor")
public class StoryCoauthor {

	@EmbeddedId
	private Key key;

	/** 누가 불러들였나 — 만든 사람이 직접 넣었을 수도, 그가 만든 링크를 눌러 들어왔을 수도 있다. */
	@Column(name = "invited_by", nullable = false, updatable = false)
	private UUID invitedBy;

	@Column(name = "joined_at", nullable = false, updatable = false)
	private Instant joinedAt;

	protected StoryCoauthor() {
	}

	public StoryCoauthor(UUID storyId, UUID userId, UUID invitedBy, Instant joinedAt) {
		this.key = new Key(storyId, userId);
		this.invitedBy = invitedBy;
		this.joinedAt = joinedAt;
	}

	public UUID getStoryId()   { return key.storyId; }
	public UUID getUserId()    { return key.userId; }
	public UUID getInvitedBy() { return invitedBy; }
	public Instant getJoinedAt() { return joinedAt; }

	@Embeddable
	public static class Key implements Serializable {

		@Column(name = "story_id", nullable = false)
		private UUID storyId;

		@Column(name = "user_id", nullable = false)
		private UUID userId;

		protected Key() {
		}

		public Key(UUID storyId, UUID userId) {
			this.storyId = storyId;
			this.userId = userId;
		}

		public UUID storyId() { return storyId; }
		public UUID userId()  { return userId; }

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (!(o instanceof Key other)) {
				return false;
			}
			return storyId.equals(other.storyId) && userId.equals(other.userId);
		}

		@Override
		public int hashCode() {
			return Objects.hash(storyId, userId);
		}
	}
}
