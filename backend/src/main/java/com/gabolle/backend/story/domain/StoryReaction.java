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
 * <p>바꾸면 행이 바뀌지 늘지 않는다. 좋아요를 눌렀다가 싫어요로 바꾸는 것은 같은 칸의 값이 바뀌는
 * 것이다. 행을 더하는 모양이면 인기순 집계가 「좋아요도 눌렀고 싫어요도 눌렀다」로 읽는다. 그 규칙은
 * {@link StoryReactionId} 가 {@code (story_id, user_id)} 인 것으로 지켜진다.
 *
 * <p>값을 바꾸는 메서드도 접근자도 일부러 두지 않았다. 이 표를 고치는 유일한 길은
 * {@code StoryReactionRepository} 의 문장들이다 — 읽고-고치고-쓰는 모양은 같은 사람이 빠르게 두 번
 * 누르면 기본 키에 부딪혀 깨진다. 여기에 수정자를 두면 그 규칙을 우회하는 두 번째 길이 생긴다.
 * JPQL 은 필드를 읽으므로 접근자도 필요 없고, 두면 다음 사람이 직접 다루는 길이 있다고 믿는다.
 */
@Entity
@Table(name = "story_reaction")
public class StoryReaction {

	@EmbeddedId
	private StoryReactionId id;

	/** {@code null} 이면 취소한 것이다. 행은 남는다 — {@code likeRecorded} 를 지키기 위해서다. */
	@Enumerated(EnumType.STRING)
	@Column(name = "reaction", length = 16)
	private ReactionType reaction;

	/** 이 사람이 이 글에 처음 손댄 때다. 인기순은 이 칸이 아니라 {@link #reactedAt} 을 쓴다. */
	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	/**
	 * 지금의 반응을 고른 때. 인기순의 24시간 창이 자르는 칸이 이것이다. {@link #createdAt} 으로 자르면
	 * 사흘 전에 싫어요를 눌렀던 사람이 오늘 좋아요로 바꿔도 시각이 사흘 전이라 집계에서 빠진다.
	 */
	@Column(name = "reacted_at", nullable = false)
	private OffsetDateTime reactedAt;

	/**
	 * 이 사람이 이 글에 좋아요를 한 번이라도 남겼나. 취소해도 안 내려간다 — 이벤트를 (글, 사람, 종류)당
	 * 하나로 묶는 칸이라, 내려가면 하트를 껐다 켜는 것으로 이벤트를 몇 번이든 다시 만들 수 있다.
	 */
	@Column(name = "like_recorded", nullable = false)
	private boolean likeRecorded;

	@Column(name = "dislike_recorded", nullable = false)
	private boolean dislikeRecorded;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected StoryReaction() {
		// JPA 전용
	}

}
