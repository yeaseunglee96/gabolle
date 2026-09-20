package com.gabolle.backend.story.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * "같이 쓰자" 링크 한 장 — {@code token} 이 곧 열쇠다. 이것을 아는 사람이 초대받은 사람이다.
 *
 * <p>표(token)의 유일성은 DB 의 {@code ux_story_invite_token} 이 보장한다 — 여기서는 값을 만들지도
 * 검사하지도 않는다. 만료 판정({@code expiresAt} 이 지금보다 앞인가)도 호출하는 쪽의 몫이라 이 엔티티는
 * 들고 있는 값만 그대로 내보낸다.
 */
@Entity
@Table(name = "story_invite")
public class StoryInvite {

	@Id
	@Column(name = "story_invite_id", nullable = false, updatable = false)
	private UUID storyInviteId;

	@Column(name = "story_id", nullable = false, updatable = false)
	private UUID storyId;

	@Column(name = "token", nullable = false, updatable = false, length = 64)
	private String token;

	@Column(name = "created_by", nullable = false, updatable = false)
	private UUID createdBy;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "expires_at", nullable = false, updatable = false)
	private Instant expiresAt;

	protected StoryInvite() {
	}

	public StoryInvite(UUID storyInviteId, UUID storyId, String token, UUID createdBy, Instant createdAt,
			Instant expiresAt) {
		if (!expiresAt.isAfter(createdAt)) {
			throw new IllegalArgumentException("만료 시각은 생성 시각보다 뒤여야 한다");
		}
		this.storyInviteId = storyInviteId;
		this.storyId = storyId;
		this.token = token;
		this.createdBy = createdBy;
		this.createdAt = createdAt;
		this.expiresAt = expiresAt;
	}

	public UUID getStoryInviteId() { return storyInviteId; }
	public UUID getStoryId()       { return storyId; }
	public String getToken()       { return token; }
	public UUID getCreatedBy()     { return createdBy; }
	public Instant getCreatedAt()  { return createdAt; }
	public Instant getExpiresAt()  { return expiresAt; }
}
