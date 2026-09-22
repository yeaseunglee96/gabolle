package com.gabolle.backend.story.domain;

import java.time.Instant;
import java.util.UUID;

import com.gabolle.backend.moderation.domain.StoryModerationState;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 여행 기록 한 건 — 사진(최대 3장)·글(최대 500자)·연결 장소·지역·공개 범위·공개 시각.
 *
 * <p>좌표 필드가 없다. "위치는 지역 단위로만 저장한다" 는 약속을 칸을 두지 않는 것으로 지킨다.
 *
 * <p>사진은 이 엔티티에 없다. {@link StoryImage} 가 {@link UploadedImage}(주소·저장 키)를 가리키고,
 * 사진 데이터는 데이터베이스 어디에도 없다.
 *
 * <p>삭제는 {@code deletedAt} 을 찍는다 — 신고·감사 기록이 가리키는 자리가 사라지지 않게.
 * 조회·피드는 {@code deletedAt IS NULL} 만 본다.
 */
@Entity
@Table(name = "story")
public class Story {

	public static final int MAX_BODY_LENGTH = 500;

	public static final int MAX_IMAGES = 3;

	@Id
	@Column(name = "story_id", nullable = false, updatable = false)
	private UUID storyId;

	@Column(name = "author_user_id", nullable = false, updatable = false)
	private UUID authorUserId;

	@Column(name = "trip_id")
	private UUID tripId;

	@Column(name = "place_id")
	private UUID placeId;

	@Column(name = "body", nullable = false, columnDefinition = "text")
	private String body;

	@Column(name = "region", length = 100)
	private String region;

	@Enumerated(EnumType.STRING)
	@Column(name = "visibility", nullable = false, length = 20)
	private StoryVisibility visibility;

	@Column(name = "publish_at", nullable = false)
	private Instant publishAt;

	/**
	 * 신고 검토 상태. {@code deletedAt} 과 다른 칸이다 — 이 칸은 기각으로 {@code VISIBLE} 로 되돌릴 수
	 * 있고 {@code deletedAt} 은 되돌리지 않는다. 한 칸에 담으면 "지운 것" 과 "잠깐 감춘 것" 이 뭉개진다.
	 */
	@Enumerated(EnumType.STRING)
	@Column(name = "moderation_state", nullable = false, length = 20)
	private StoryModerationState moderationState = StoryModerationState.VISIBLE;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	/**
	 * 마지막으로 본문을 고친 사람. 여럿이 함께 쓰는 기록에서 동시에 고치면 나중에 저장한 쪽이 이기고,
	 * 충돌을 감지해 멈추지 않는다. 그 대신 누가 고쳤는지를 이 칸에 남긴다.
	 *
	 * <p>아무도 고친 적 없는 기록에서는 {@code null} 이다. 만든 사람으로 채우지 않는다.
	 */
	@Column(name = "last_edited_by")
	private UUID lastEditedBy;

	/**
	 * 댓글이면 부모 글, 원글이면 {@code null}. 댓글의 댓글도 이 칸 하나로 이어진다 — 깊이 제한은 없고
	 * DB 도 깊이를 모른다. 몇 단까지 보여줄지는 화면이 정한다.
	 */
	@Column(name = "parent_story_id", updatable = false)
	private UUID parentStoryId;

	/**
	 * 직접 달린 댓글 수. 손자 이하는 안 센다 — 손자까지 세면 댓글 하나를 지울 때 조상을 전부 거슬러
	 * 올라가며 내려야 하고, 직접 달린 것만 세면 고치는 자리가 깊이와 무관하게 언제나 한 칸이다.
	 *
	 * <p>낱개를 세지 않고 칸에 담는 이유는 피드 한 장에 글이 여럿이라, 글마다 자식을 세면 그 수만큼
	 * 질의가 붙어서다.
	 */
	@Column(name = "reply_count", nullable = false)
	private int replyCount;

	/**
	 * 글을 눌러서 연 횟수. 노출 수가 아니다. 사람 × 글 × 하루 한 번이고, 작성자 본인은 안 세고,
	 * 비회원은 익명 세션으로 식별해 센다.
	 *
	 * <p>낱개는 {@code story_view} 에 90일만 남고 그 뒤에는 이 칸만 남는다 — 되찾을 수 없다.
	 */
	@Column(name = "view_count", nullable = false)
	private int viewCount;

	/**
	 * 공유 링크 복사 버튼을 누른 횟수. 화면에서 부르는 말은 「인용수」지만 우리가 아는 것은 버튼을
	 * 눌렀다는 것뿐이라 이름은 {@code linkCopyCount} 다.
	 */
	@Column(name = "link_copy_count", nullable = false)
	private int linkCopyCount;

	protected Story() {
	}

	public Story(UUID storyId, UUID authorUserId, UUID tripId, UUID placeId, String body, String region,
			StoryVisibility visibility, Instant publishAt, Instant now) {
		if (storyId == null || authorUserId == null || visibility == null || publishAt == null || now == null) {
			throw new IllegalArgumentException("storyId·authorUserId·visibility·publishAt·now 는 필수다");
		}
		this.storyId = storyId;
		this.authorUserId = authorUserId;
		this.tripId = tripId;
		this.placeId = placeId;
		this.body = requireBody(body);
		this.region = region;
		this.visibility = visibility;
		this.publishAt = publishAt;
		this.createdAt = now;
		this.updatedAt = now;
	}

	/**
	 * 댓글을 만든다. 언제나 {@code PUBLIC} · {@code publishAt = now} 다 — 두 칸이 {@code NOT NULL}
	 * 이라 자리는 채워야 하는데 댓글에는 그 개념이 없어서다. 실제로 보이는가는 부모의 공개범위가 정한다.
	 *
	 * <p>원글 생성자에 {@code parentStoryId} 를 붙이지 않고 통로를 따로 둔 이유가 그것이다. 붙이면
	 * 부르는 쪽이 댓글에 {@code FOLLOWERS} 를 줄 수 있고, 그것을 막는 검사를 또 어딘가에 둬야 한다.
	 */
	public static Story reply(UUID storyId, UUID authorUserId, UUID parentStoryId, String body, Instant now) {
		if (parentStoryId == null) {
			throw new IllegalArgumentException("parentStoryId 는 필수다 — 부모가 없으면 댓글이 아니라 원글이다");
		}
		Story reply = new Story(storyId, authorUserId, null, null, body, null,
				StoryVisibility.PUBLIC, now, now);
		reply.parentStoryId = parentStoryId;
		return reply;
	}

	/** 댓글인가. {@code parent_story_id} 하나로 갈린다 — 깊이는 안 본다. */
	public boolean isReply() {
		return parentStoryId != null;
	}

	/** 부모에게만 부른다. 할아버지까지 올라가지 않는다 — 이 칸의 뜻이 「직접 달린 것」이다. */
	public void addReply() {
		this.replyCount++;
	}

	/**
	 * 0 아래로 안 내려간다. DB 에도 같은 제약이 있지만({@code ck_story_reply_count}) 여기서 먼저
	 * 막아야 어느 댓글을 지우다 그랬는지가 남는다.
	 */
	public void removeReply() {
		if (this.replyCount > 0) {
			this.replyCount--;
		}
	}

	/**
	 * 조회 낱개를 넣는 것과 같은 트랜잭션에서 부른다. 따로 세면 「열리긴 했는데 수가 안 오른」 상태가 생긴다.
	 *
	 * <p>내려가지 않는다. 낱개는 90일 뒤에 지워지지만 누적은 누적이라, 지우면서 이 칸을 같이 내리면
	 * 어제까지의 조회가 사라진다.
	 */
	public void recordView() {
		this.viewCount++;
	}

	/** {@link #recordView()} 와 같은 규칙이다. */
	public void recordLinkCopy() {
		this.linkCopyCount++;
	}

	private static String requireBody(String body) {
		if (body == null || body.isBlank()) {
			throw new IllegalArgumentException("글이 비어 있다");
		}
		if (body.codePointCount(0, body.length()) > MAX_BODY_LENGTH) {
			throw new IllegalArgumentException("글은 " + MAX_BODY_LENGTH + "자를 넘을 수 없다");
		}
		return body;
	}

	/**
	 * 수정. 넘긴 값 중 {@code null} 은 "바꾸지 않는다" 다.
	 *
	 * <p>{@code editor} 를 인자로 받는 것은 부르는 쪽이 누가 고치는지 말하지 않고는 못 고치게 하려는
	 * 것이다. 나중에 채우게 두면 어느 경로 하나가 빼먹었을 때 그 기록만 조용히 빈다.
	 *
	 * <p>공개 범위·공개 시각을 만든 사람만 바꿀 수 있다는 규칙은 여기서 재지 않는다. 이 클래스는
	 * 누가 참여자인지 모른다. 그 판정은 서비스가 한다.
	 */
	public void edit(String body, String region, StoryVisibility visibility, Instant publishAt, UUID placeId,
			boolean clearPlace, UUID editor, Instant now) {
		if (body != null) {
			this.body = requireBody(body);
		}
		if (region != null) {
			this.region = region.isBlank() ? null : region;
		}
		if (visibility != null) {
			this.visibility = visibility;
		}
		if (publishAt != null) {
			this.publishAt = publishAt;
		}
		if (clearPlace) {
			this.placeId = null;
		}
		else if (placeId != null) {
			this.placeId = placeId;
		}
		this.lastEditedBy = editor;
		this.updatedAt = now;
	}

	public void markDeleted(Instant now) {
		this.deletedAt = now;
		this.updatedAt = now;
	}

	public boolean isDeleted() {
		return deletedAt != null;
	}

	public boolean isPublishedAt(Instant now) {
		return !publishAt.isAfter(now);
	}

	public boolean isAuthor(UUID userId) {
		return authorUserId.equals(userId);
	}

	public UUID getStoryId()          { return storyId; }
	public UUID getAuthorUserId()     { return authorUserId; }
	public UUID getTripId()           { return tripId; }
	public UUID getPlaceId()          { return placeId; }
	public String getBody()           { return body; }
	public String getRegion()         { return region; }
	public StoryVisibility getVisibility() { return visibility; }
	public Instant getPublishAt()     { return publishAt; }
	public Instant getCreatedAt()     { return createdAt; }
	public Instant getUpdatedAt()     { return updatedAt; }
	public Instant getDeletedAt()     { return deletedAt; }
	public UUID getLastEditedBy()     { return lastEditedBy; }
	public UUID getParentStoryId()    { return parentStoryId; }
	public int getReplyCount()        { return replyCount; }
	public int getViewCount()         { return viewCount; }
	public int getLinkCopyCount()     { return linkCopyCount; }

	/** 신고를 받아 검토 대기로 바꾼다. 이미 대기·삭제 상태면 아무것도 하지 않는다. */
	public void markUnderReview() {
		if (this.moderationState == StoryModerationState.VISIBLE) {
			this.moderationState = StoryModerationState.UNDER_REVIEW;
		}
	}

	/** 운영자가 기각했다 — 다시 보인다. */
	public void restoreVisibility() {
		this.moderationState = StoryModerationState.VISIBLE;
	}

	/** 운영자가 삭제로 처리했다. 되돌리지 않는다. */
	public void markRemovedByModerator() {
		this.moderationState = StoryModerationState.REMOVED;
	}

	public StoryModerationState getModerationState() {
		return this.moderationState;
	}
}
