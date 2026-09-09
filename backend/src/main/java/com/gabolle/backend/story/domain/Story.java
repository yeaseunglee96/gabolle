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
 * <p>🔴 좌표 필드가 없다. "위치는 지역 단위로만 저장한다" 는 약속을 이 클래스와 {@code story} 표가
 * 같은 모양으로 지킨다 — 칸이 없으면 실수로도 못 넣는다(S15P21E201-207).
 *
 * <p>🔴 사진은 이 엔티티에 없다. {@link StoryImage} 가 {@link UploadedImage}(주소·저장 키)를
 * 가리킨다. 사진 데이터는 데이터베이스 어디에도 없다(S15P21E201-174).
 *
 * <p>삭제는 {@code deletedAt} 을 찍는다. 행을 지우지 않는 이유는 {@code app_user} 와 같다 — 신고·
 * 감사 기록이 가리키는 자리가 사라지지 않게. 조회·피드는 {@code deletedAt IS NULL} 만 본다.
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
	 * 신고 검토 상태 — S15P21E201-254 · -267.
	 *
	 * <p>🔴 {@code deletedAt} 과 다른 칸이다. 이 칸은 기각으로 {@code VISIBLE} 로 <b>되돌릴 수
	 * 있고</b>, {@code deletedAt} 은 작성자가 지운 것이라 되돌리지 않는다. 한 칸에 담으면 "지운
	 * 것" 과 "잠깐 감춘 것" 을 구분할 수 없다.
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

	private static String requireBody(String body) {
		if (body == null || body.isBlank()) {
			throw new IllegalArgumentException("글이 비어 있다");
		}
		if (body.codePointCount(0, body.length()) > MAX_BODY_LENGTH) {
			throw new IllegalArgumentException("글은 " + MAX_BODY_LENGTH + "자를 넘을 수 없다");
		}
		return body;
	}

	/** 수정. 넘긴 값 중 {@code null} 은 "바꾸지 않는다" 다. */
	public void edit(String body, String region, StoryVisibility visibility, Instant publishAt, UUID placeId,
			boolean clearPlace, Instant now) {
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

	/** 신고를 받아 검토 대기로 바꾼다. 이미 대기·삭제 상태면 아무것도 하지 않는다. */
	public void markUnderReview() {
		if (this.moderationState == StoryModerationState.VISIBLE) {
			this.moderationState = StoryModerationState.UNDER_REVIEW;
		}
	}

	/** 운영자가 기각했다 — 다시 보인다 (S15P21E201-267). */
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
