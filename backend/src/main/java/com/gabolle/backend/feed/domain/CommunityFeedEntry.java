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
 * <p>홈({@link UserFeedEntry})과 표를 나눈 이유는 <b>가리키는 것이 다르기</b> 때문이다.
 * 홈은 장소·일정이고 여기는 글·글쓴이다. 한 표에 넣고 종류 칸으로 가르면 어느 쪽에도
 * 안 맞는 빈 칸이 절반씩 생긴다.
 *
 * <p>🔴 <b>{@code postId} 에 FK 가 없다. 글 표({@code post})가 아직 없기 때문이다.</b>
 * 없는 표를 가리키는 FK 는 만들 수 없고, 가짜 글 표를 이 티켓에서 만들면 커뮤니티
 * 담당자와 주인이 둘이 된다. S15P21E201-543 이 {@code preference_snapshot_id} 를 같은
 * 이유로 FK 없이 두었던 것과 같은 판단이고, 그때처럼 <b>이름을 맞춰 두었으므로</b>
 * 글 표가 생기면 {@code ALTER TABLE} 한 줄로 FK 만 더하면 된다.
 */
@Entity
@Table(name = "community_feed")
public class CommunityFeedEntry {

	@EmbeddedId
	private FeedEntryId id;

	@Column(name = "post_id", nullable = false, updatable = false)
	private UUID postId;

	/**
	 * 글쓴이.
	 *
	 * <p>🔴 글에서 다시 꺼내지 않고 여기 복사해 두는 이유가 있다. 차단·숨김을 적용할 때
	 * <b>글을 조회하지 않고</b> 걸러낼 수 있어야 하기 때문이다. 걸러내려고 글을 전부
	 * 불러오면 이 티켓이 없애려던 비용이 그대로 돌아온다.
	 */
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
