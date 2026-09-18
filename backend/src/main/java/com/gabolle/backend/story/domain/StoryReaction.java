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
 *
 * <p>🔴 <b>접근자(getter)가 하나도 없다 — 빠뜨린 것이 아니다</b>(S15P21E201-1249). JPQL 은
 * {@code r.reaction}·{@code r.reactedAt} 처럼 <b>필드</b>를 읽으므로 접근자가 필요 없고,
 * 처음에 만들어 둔 일곱 개는 호출자가 하나도 없었다. 필요해지는 날 그때 만든다 —
 * 안 쓰는 접근자를 미리 두면 다음 사람이 그것을 읽고 <b>이 엔티티를 직접 다루는 길이
 * 있다고 믿는다.</b> 이 표를 고치는 유일한 길은 {@code StoryReactionRepository} 의 문장들이다.
 */
@Entity
@Table(name = "story_reaction")
public class StoryReaction {

	@EmbeddedId
	private StoryReactionId id;

	/** 🔴 {@code null} 이면 <b>취소한 것</b>이다. 행은 남는다 — 아래 {@code likeRecorded} 를 지키기 위해서다. */
	@Enumerated(EnumType.STRING)
	@Column(name = "reaction", length = 16)
	private ReactionType reaction;

	/** 🔴 이 사람이 이 글에 <b>처음 손댄</b> 때다. 인기순은 이 칸을 안 쓴다 — {@link #reactedAt} 을 쓴다. */
	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	/**
	 * 🔴 <b>지금의 반응을 고른 때.</b> 인기순의 24시간 창이 자르는 칸이 이것이다.
	 *
	 * <p>한때 {@link #createdAt} 이 그 일을 겸했는데, 그러면 사흘 전에 싫어요를 눌렀던 사람이
	 * 오늘 좋아요로 바꿔도 시각이 사흘 전이라 <b>오늘 눌린 좋아요가 집계에서 빠졌다.</b>
	 */
	@Column(name = "reacted_at", nullable = false)
	private OffsetDateTime reactedAt;

	/**
	 * 🔴 이 사람이 이 글에 좋아요를 <b>한 번이라도</b> 남겼나. <b>취소해도 안 내려간다.</b>
	 *
	 * <p>이벤트를 (글, 사람, 종류)당 하나로 묶는 칸이다. 내려가면 하트를 껐다 켜는 것으로
	 * 이벤트를 몇 번이든 다시 만들 수 있다 — 실제로 그랬다.
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
