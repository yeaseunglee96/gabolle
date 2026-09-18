package com.gabolle.backend.story.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 글을 한 번 연 기록 — S15P21E201-1201.
 *
 * <h2>왜 낱개를 따로 두나</h2>
 *
 * 누적은 {@link Story#getViewCount()} 한 칸이면 된다. 낱개가 따로 있는 이유는 <b>「하루 한 번」을
 * 지키기 위해서</b>다. 누가 오늘 이미 봤는지를 알아야 두 번 세지 않는데, 그건 칸 하나로는 답할 수
 * 없다.
 *
 * <p>그래서 낱개는 <b>90일만</b> 남는다. 더 오래 두어도 「하루 한 번」에는 쓸모가 없고, 사람이
 * 무엇을 언제 읽었는지가 필요 이상으로 오래 남는다.
 *
 * <h2>🔴 보는 사람이 두 종류다</h2>
 *
 * {@code userId} 와 {@code anonymousSessionId} 중 <b>정확히 하나만</b> 차 있다. DB 의
 * {@code ck_story_view_viewer} 가 그것을 강제한다 — 둘 다 비면 누가 봤는지 모르는 행이 되고,
 * 둘 다 차면 같은 조회가 두 사람으로 세어진다.
 *
 * <p>비회원을 세는 것은 정해진 규칙이다. 로그인하지 않고 읽는 사람이 이 서비스에서는 다수다.
 *
 * <h2>🔴 하루의 경계는 KST 다</h2>
 *
 * {@code viewedOn} 을 DB 의 {@code current_date} 로 채우지 않는다. 그 값은 서버 시간대를 따르는데
 * 사용자의 하루는 한국 시각이다. UTC 로 세면 오전 9시에 날짜가 바뀐다. 마이그레이션 주석에 같은
 * 이유가 적혀 있다.
 *
 * <h2>중복은 DB 가 막는다</h2>
 *
 * 응용이 「오늘 것이 있나」를 읽고 없으면 넣는 모양은 <b>같은 사람이 두 기기에서 동시에 열면 둘 다
 * 통과한다.</b> 조건부 유일 색인({@code ux_story_view_member}·{@code ux_story_view_anonymous})이
 * 그 경주를 끝에서 막고, 응용은 중복 키를 「이미 셌다」로 읽으면 된다.
 */
@Entity
@Table(name = "story_view")
public class StoryView {

	@Id
	@Column(name = "story_view_id", nullable = false, updatable = false)
	private UUID storyViewId;

	@Column(name = "story_id", nullable = false, updatable = false)
	private UUID storyId;

	/** 회원이 봤으면 그 사람. 비회원이면 {@code null} 이고 {@link #anonymousSessionId} 가 찬다. */
	@Column(name = "user_id", updatable = false)
	private UUID userId;

	/** 비회원이 봤으면 그 익명 세션. 회원이면 {@code null} 이다. */
	@Column(name = "anonymous_session_id", updatable = false)
	private UUID anonymousSessionId;

	/** KST 기준 날짜. 「사람 × 글 × 하루 한 번」의 그 하루다. */
	@Column(name = "viewed_on", nullable = false, updatable = false)
	private LocalDate viewedOn;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected StoryView() {
	}

	/** 로그인한 사람이 열었다. */
	public static StoryView byMember(UUID storyId, UUID userId, LocalDate viewedOn, Instant now) {
		return create(storyId, userId, null, viewedOn, now);
	}

	/** 로그인하지 않은 사람이 열었다 — 익명 세션으로 식별한다. */
	public static StoryView byAnonymous(UUID storyId, UUID anonymousSessionId, LocalDate viewedOn, Instant now) {
		return create(storyId, null, anonymousSessionId, viewedOn, now);
	}

	private static StoryView create(UUID storyId, UUID userId, UUID anonymousSessionId, LocalDate viewedOn,
			Instant now) {
		// 🔴 DB 의 CHECK 와 같은 것을 여기서 먼저 막는다. DB 까지 가면 트랜잭션이 통째로
		//    되돌려지고 원인은 제약 이름만 남는다 — Story.removeReply 가 같은 이유로 같은 모양이다.
		if ((userId == null) == (anonymousSessionId == null)) {
			throw new IllegalArgumentException("본 사람은 회원이거나 익명 세션이거나 둘 중 하나여야 한다");
		}
		StoryView view = new StoryView();
		view.storyViewId = UUID.randomUUID();
		view.storyId = storyId;
		view.userId = userId;
		view.anonymousSessionId = anonymousSessionId;
		view.viewedOn = viewedOn;
		view.createdAt = now;
		return view;
	}

	public UUID getStoryViewId()        { return storyViewId; }
	public UUID getStoryId()            { return storyId; }
	public UUID getUserId()             { return userId; }
	public UUID getAnonymousSessionId() { return anonymousSessionId; }
	public LocalDate getViewedOn()      { return viewedOn; }
	public Instant getCreatedAt()       { return createdAt; }
}
