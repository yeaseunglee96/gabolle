package com.gabolle.backend.story.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 글의 공유 링크를 한 번 복사한 기록.
 *
 * <p>화면에서 부르는 말은 「인용수」지만 표와 칸 이름은 {@code link_copy} 다. 우리가 아는 것은 복사
 * 버튼을 눌렀다는 것뿐이고, 인용했는지 어디에 붙였는지는 모른다.
 *
 * <p>{@link StoryView} 와 모양이 같은데도 표를 나눈 것은 규칙이 갈릴 때를 위해서다 — 보관 기간이나
 * 세는 방식이 한쪽만 바뀌면 합쳐 둔 표는 한쪽 규칙을 다른 쪽에 강요한다. 좋아요·싫어요를 한 표에 둔
 * 것과 다른 판단인데, 그쪽은 같은 질문의 두 답이고 이쪽은 같은 순간에 둘 다 할 수 있는 다른 행동이다.
 *
 * <p>세는 규칙은 조회와 같다. 나머지 규칙과 근거는 {@link StoryView} 에 있다 — 보는 사람이 두 종류인
 * 것, 하루의 경계가 KST 인 것, 중복을 DB 가 막는 것.
 */
@Entity
@Table(name = "story_link_copy")
public class StoryLinkCopy {

	@Id
	@Column(name = "story_link_copy_id", nullable = false, updatable = false)
	private UUID storyLinkCopyId;

	@Column(name = "story_id", nullable = false, updatable = false)
	private UUID storyId;

	/** 회원이 복사했으면 그 사람. 비회원이면 {@code null} 이다. */
	@Column(name = "user_id", updatable = false)
	private UUID userId;

	/** 비회원이 복사했으면 그 익명 세션. 회원이면 {@code null} 이다. */
	@Column(name = "anonymous_session_id", updatable = false)
	private UUID anonymousSessionId;

	/** KST 기준 날짜. 「사람 × 글 × 하루 한 번」의 그 하루다. */
	@Column(name = "copied_on", nullable = false, updatable = false)
	private LocalDate copiedOn;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected StoryLinkCopy() {
	}

	/** 로그인한 사람이 복사했다. */
	public static StoryLinkCopy byMember(UUID storyId, UUID userId, LocalDate copiedOn, Instant now) {
		return create(storyId, userId, null, copiedOn, now);
	}

	/** 로그인하지 않은 사람이 복사했다 — 익명 세션으로 식별한다. */
	public static StoryLinkCopy byAnonymous(UUID storyId, UUID anonymousSessionId, LocalDate copiedOn, Instant now) {
		return create(storyId, null, anonymousSessionId, copiedOn, now);
	}

	private static StoryLinkCopy create(UUID storyId, UUID userId, UUID anonymousSessionId, LocalDate copiedOn,
			Instant now) {
		// DB 의 CHECK 와 같은 것을 여기서 먼저 막는다 — StoryView 와 같은 이유다.
		if ((userId == null) == (anonymousSessionId == null)) {
			throw new IllegalArgumentException("복사한 사람은 회원이거나 익명 세션이거나 둘 중 하나여야 한다");
		}
		StoryLinkCopy copy = new StoryLinkCopy();
		copy.storyLinkCopyId = UUID.randomUUID();
		copy.storyId = storyId;
		copy.userId = userId;
		copy.anonymousSessionId = anonymousSessionId;
		copy.copiedOn = copiedOn;
		copy.createdAt = now;
		return copy;
	}

	public UUID getStoryLinkCopyId()    { return storyLinkCopyId; }
	public UUID getStoryId()            { return storyId; }
	public UUID getUserId()             { return userId; }
	public UUID getAnonymousSessionId() { return anonymousSessionId; }
	public LocalDate getCopiedOn()      { return copiedOn; }
	public Instant getCreatedAt()       { return createdAt; }
}
