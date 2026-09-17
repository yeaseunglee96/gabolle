package com.gabolle.backend.story.domain;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * 한 사람이 한 글에 단 반응.
 *
 * <p>🔴 <b>바꾸면 행이 바뀌지 늘지 않는다.</b> 좋아요를 눌렀다가 싫어요로 바꾸는 것은
 * 새 사건이 아니라 <b>같은 칸의 값이 바뀌는 것</b>이다. 행을 더하는 모양이면 인기순 집계가
 * 「이 사람은 좋아요도 눌렀고 싫어요도 눌렀다」로 읽힌다. 그 규칙은 키가 지킨다 —
 * {@link StoryReactionId} 가 {@code (story_id, user_id)} 다.
 *
 * <p>🔴 <b>이 클래스에는 값을 바꾸는 메서드가 없다.</b> 쓰는 쪽은 전부
 * {@code StoryReactionRepository.upsert} 한 문장을 지난다 — 읽고-고치고-쓰는 모양은 같은
 * 사람이 빠르게 두 번 누르면 기본 키에 부딪혀 깨지기 때문이다. 여기에 수정자를 두면
 * <b>그 규칙을 우회하는 두 번째 길</b>이 생기고, 둘 중 하나만 고쳐진 채로 남는다.
 * 이 엔티티는 집계 질의({@code countRecentLikes})가 읽는 표 모양이다.
 */
@Entity
@Table(name = "story_reaction")
public class StoryReaction {

	@EmbeddedId
	private StoryReactionId id;

	@Enumerated(EnumType.STRING)
	@Column(name = "reaction", nullable = false, length = 16)
	private ReactionType reaction;

	/** 🔴 처음 누른 시각이다. 종류를 바꿔도 안 바뀐다 — 인기순이 이 칸으로 24시간 창을 자른다. */
	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected StoryReaction() {
		// JPA 전용
	}

	public StoryReactionId getId() {
		return this.id;
	}

	public ReactionType getReaction() {
		return this.reaction;
	}

	public OffsetDateTime getCreatedAt() {
		return this.createdAt;
	}

	public OffsetDateTime getUpdatedAt() {
		return this.updatedAt;
	}
}
