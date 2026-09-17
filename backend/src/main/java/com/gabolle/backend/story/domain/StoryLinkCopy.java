package com.gabolle.backend.story.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 글의 공유 링크를 한 번 복사한 기록 — S15P21E201-1201.
 *
 * <h2>🔴 이름이 인용이 아니라 복사인 이유</h2>
 *
 * 화면에서 부르는 말은 <b>「인용수」</b>지만 표와 칸 이름은 {@code link_copy} 다.
 * <b>우리가 아는 것은 「복사 버튼을 눌렀다」뿐이다</b> — 복사한 사람이 인용했는지, 어디에
 * 붙였는지, 붙이긴 했는지 우리는 모른다. 이름이 모르는 것을 주장하면 나중에 그 이름을 믿고
 * 판단하는 사람이 생긴다.
 *
 * <p>화면에 보이는 말은 따로 정해도 된다. 바꾸기 쉬운 쪽은 화면이고, 표 이름은 오래 간다.
 *
 * <h2>🔴 {@link StoryView} 와 왜 표를 나눴나</h2>
 *
 * 모양이 같아서 <b>한 표에 종류 칸을 두는 안</b>도 있었다. 나눈 이유는 <b>규칙이 갈릴 때</b>다 —
 * 보관 기간이나 세는 방식이 한쪽만 바뀌면, 합쳐 둔 표는 한쪽 규칙을 다른 쪽에 강요한다.
 *
 * <p>{@code story_reaction} 이 좋아요·싫어요를 <b>한 표에 둔 것과는 다른 판단</b>인데, 그쪽은
 * 둘이 <b>같은 질문의 두 답</b>이고 이쪽은 <b>서로 다른 행동</b>이다. 읽는 것과 퍼뜨리는 것은
 * 같은 사람이 같은 순간에 둘 다 할 수 있다.
 *
 * <p>나머지 규칙과 그 근거는 {@link StoryView} 에 적혀 있다 — 보는 사람이 두 종류인 것,
 * 하루의 경계가 KST 인 것, 중복을 DB 가 막는 것.
 *
 * <h2>🔴 알고 통일한 것이지 모르고 통일한 것이 아니다</h2>
 *
 * 정해진 규칙 넷 중 둘은 <b>「조회」라고 못박혀 있다</b> — <i>「작성자 본인 <b>조회</b>는 안
 * 센다」</i>, <i>「비회원 <b>조회</b>는 센다」</i>. <b>링크 복사에도 같은지는 적혀 있지 않다.</b>
 *
 * <p>표 모양은 조회와 같게 두었다. 한 규칙 표 아래 한 제목으로 묶여 있으니 그것이 지어내지
 * 않는 읽기다. 다만 <b>「작성자가 자기 글 링크를 복사하는 것」</b>은 조회와 성격이 다를 수
 * 있다 — 자기 글을 퍼뜨리는 것은 정상 행동이라서다.
 *
 * <p>🔴 <b>세는 코드(2부)가 그 자리에서 갈린다.</b> 이 파일은 표 모양만 정하므로 아직 안
 * 갈린다. 2부를 쓰는 사람은 이 물음을 사람에게 먼저 묻는다 — 답이 「조회와 다르다」로
 * 나와도 이 표는 안 바뀐다.
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
		// 🔴 DB 의 CHECK 와 같은 것을 여기서 먼저 막는다 — StoryView 와 같은 이유다.
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
