package com.gabolle.backend.feed.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * 커뮤니티에 들어갔을 때 보이는 글 한 줄.
 *
 * <p>홈({@link UserFeedEntry})과 표를 나눈 이유는 가리키는 것이 달라서다. 한 표에 넣고
 * 종류 칸으로 가르면 어느 쪽에도 안 맞는 빈 칸이 절반씩 생긴다.
 *
 * <p>{@code postId} 에 FK 가 없다. 글 표({@code post})가 아직 없기 때문이다. 이름은
 * 맞춰 두었으므로 글 표가 생기면 {@code ALTER TABLE} 한 줄로 FK 만 더하면 된다.
 */
@Entity
@Table(name = "community_feed")
public class CommunityFeedEntry {

	@EmbeddedId
	private FeedEntryId id;

	@Column(name = "post_id", nullable = false, updatable = false)
	private UUID postId;

	/** 글에서 꺼내지 않고 복사해 둔다. 차단·숨김을 글 조회 없이 걸러낼 수 있어야 한다. */
	@Column(name = "author_id", nullable = false, updatable = false)
	private UUID authorId;

	@Column(name = "score")
	private Double score;

	/** 왜 이 글이 여기 있는가. 빌 수 없다 — DB 가 막는다. */
	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "reason_codes", nullable = false)
	private String[] reasonCodes;

	/** 목록에서 글 한 줄을 그리는 데 필요한 값의 사본 (제목·미리보기·썸네일 등). */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "payload", nullable = false)
	private String payload;

	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	protected CommunityFeedEntry() {
	}

	private CommunityFeedEntry(FeedEntryId id, UUID postId, UUID authorId, Double score, String[] reasonCodes,
			String payload, OffsetDateTime createdAt) {
		this.id = id;
		this.postId = postId;
		this.authorId = authorId;
		this.score = score;
		this.reasonCodes = reasonCodes;
		this.payload = payload;
		this.createdAt = createdAt;
	}

	public static CommunityFeedEntry of(UUID buildId, int position, UUID postId, UUID authorId, Double score,
			String[] reasonCodes, String payload, OffsetDateTime createdAt) {
		return new CommunityFeedEntry(new FeedEntryId(buildId, position), postId, authorId, score, reasonCodes, payload,
				createdAt);
	}

	public FeedEntryId getId() {
		return this.id;
	}

	public UUID getBuildId() {
		return this.id.getBuildId();
	}

	public int getPosition() {
		return this.id.getPosition();
	}

	public UUID getPostId() {
		return this.postId;
	}

	public UUID getAuthorId() {
		return this.authorId;
	}

	public Double getScore() {
		return this.score;
	}

	public String[] getReasonCodes() {
		return this.reasonCodes;
	}

	public String getPayload() {
		return this.payload;
	}

	public OffsetDateTime getCreatedAt() {
		return this.createdAt;
	}
}
