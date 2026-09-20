package com.gabolle.backend.story.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 기록에 붙은 동영상 — 한 기록에 0개 또는 1개. 재생 파일은 {@link UploadedVideo} 에, 썸네일은
 * {@link UploadedImage} 에 있고 여기는 가리키기만 한다.
 *
 * <p>썸네일 id 를 여기 둔다. 기록을 지울 때 이 표를 걸어 파일을 찾으므로, 여기 없는 썸네일은
 * 지우는 길이 없어 저장소에 남는다. 그렇다고 {@link StoryImage} 로 붙이면 「한 기록에 사진
 * 3장」 한 칸을 먹는다.
 *
 * <p>두 외래키에 {@code ON DELETE} 가 없다. 탈퇴가 {@code uploaded_video}·
 * {@code uploaded_image} 를 지우기 전에 이 행을 먼저 지워야 하고, 순서를 틀리면 탈퇴 전체가
 * 외래키 위반으로 실패한다.
 */
@Entity
@Table(name = "story_video")
public class StoryVideo {

	@Id
	@Column(name = "story_video_id", nullable = false, updatable = false)
	private UUID storyVideoId;

	@Column(name = "story_id", nullable = false, updatable = false)
	private UUID storyId;

	@Column(name = "uploaded_video_id", nullable = false, updatable = false)
	private UUID uploadedVideoId;

	/**
	 * 썸네일 파일의 업로드 id. {@code uploaded_image} 를 가리킨다 — 썸네일은 사진 창구로 올라온다.
	 *
	 * <p>앱이 썸네일을 못 만들면 {@code null} 이고 그것이 정상 상태다. {@code null} 이면 지우는
	 * 쪽({@code StoryService.deleteVideoFiles})이 건너뛰어야 한다.
	 */
	@Column(name = "thumbnail_upload_id", nullable = false, updatable = false)
	private UUID thumbnailUploadId;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected StoryVideo() {
	}

	public StoryVideo(UUID storyVideoId, UUID storyId, UUID uploadedVideoId, UUID thumbnailUploadId,
			Instant createdAt) {
		// thumbnailUploadId 는 필수 검사에 넣지 않는다 — 없을 수 있는 칸이다.
		if (storyVideoId == null || storyId == null || uploadedVideoId == null || createdAt == null) {
			throw new IllegalArgumentException("storyVideoId·storyId·uploadedVideoId·createdAt 는 필수다");
		}
		this.storyVideoId = storyVideoId;
		this.storyId = storyId;
		this.uploadedVideoId = uploadedVideoId;
		this.thumbnailUploadId = thumbnailUploadId;
		this.createdAt = createdAt;
	}

	public UUID getStoryVideoId()      { return storyVideoId; }
	public UUID getStoryId()           { return storyId; }
	public UUID getUploadedVideoId()   { return uploadedVideoId; }
	public UUID getThumbnailUploadId() { return thumbnailUploadId; }
	public Instant getCreatedAt()      { return createdAt; }
}
