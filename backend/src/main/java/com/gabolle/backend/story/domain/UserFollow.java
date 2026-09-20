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
 * 팔로우 관계 한 줄 — {@code follower} 가 {@code followee} 를 팔로우한다.
 *
 * <p>기본키가 두 사람의 쌍이다. 같은 요청이 두 번 와도 두 줄이 생길 수 없는 것은 코드가 아니라
 * 이 키가 보장한다. 코드는 "이미 있으면 그대로 두고 성공으로 답한다" 만 한다.
 */
@Entity
@Table(name = "user_follow")
public class UserFollow {

	@EmbeddedId
	private Key key;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected UserFollow() {
	}

	public UserFollow(UUID followerUserId, UUID followeeUserId, Instant createdAt) {
		if (followerUserId.equals(followeeUserId)) {
			throw new SelfFollowException(followerUserId);
		}
		this.key = new Key(followerUserId, followeeUserId);
		this.createdAt = createdAt;
	}

	public UUID getFollowerUserId() { return key.followerUserId; }
	public UUID getFolloweeUserId() { return key.followeeUserId; }
	public Instant getCreatedAt()   { return createdAt; }

	@Embeddable
	public static class Key implements Serializable {

		@Column(name = "follower_user_id", nullable = false)
		private UUID followerUserId;

		@Column(name = "followee_user_id", nullable = false)
		private UUID followeeUserId;

		protected Key() {
		}

		public Key(UUID followerUserId, UUID followeeUserId) {
			this.followerUserId = followerUserId;
			this.followeeUserId = followeeUserId;
		}

		public UUID followerUserId() { return followerUserId; }
		public UUID followeeUserId() { return followeeUserId; }

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (!(o instanceof Key other)) {
				return false;
			}
			return followerUserId.equals(other.followerUserId) && followeeUserId.equals(other.followeeUserId);
		}

		@Override
		public int hashCode() {
			return Objects.hash(followerUserId, followeeUserId);
		}
	}

	/** 자기 자신을 팔로우하려 했다 — 400 으로 답할 자리다. */
	public static class SelfFollowException extends RuntimeException {

		public SelfFollowException(UUID userId) {
			super("자기 자신은 팔로우할 수 없습니다.");
		}
	}
}
