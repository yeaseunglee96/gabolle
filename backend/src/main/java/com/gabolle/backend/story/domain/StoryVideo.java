package com.gabolle.backend.story.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 기록에 붙은 동영상 — 한 기록에 <b>0개 또는 1개</b>. S15P21E201-1275.
 *
 * <p>재생 파일은 {@link UploadedVideo} 에, 썸네일은 {@link UploadedImage} 에 있고 여기는
 * <b>가리키기만</b> 한다. {@link StoryImage} 와 같은 모양이다.
 *
 * <h2>🔴 썸네일 id 를 여기가 들고 있어야 하는 이유 — 안 그러면 파일이 샌다</h2>
 *
 * 기록을 지울 때 {@code StoryService.delete} 는 <b>표를 걸어서</b> 파일을 찾는다. 표에 없는
 * 파일은 <b>지우는 길이 아예 없고</b>, 기록에 안 붙은 업로드를 쓸어 가는 배치도 이 저장소에
 * 없다(2026-09-18 확인 — 0곳). 그래서 썸네일 id 가 여기 없으면 저장소에 <b>영원히 남고</b>
 * {@code uploaded_image.deleted_at} 도 영영 {@code null} 이라 <b>지워진 것으로 보이지도 않는다.</b>
 *
 * <h2>🔴 썸네일을 {@link StoryImage} 로 붙이지 않는 이유</h2>
 *
 * 붙이면 「한 기록에 사진 3장」 한 칸을 먹는다. 동영상을 넣으면 사진이 2장만 들어가는 셈인데
 * 사용자에게 설명할 수 없다. 썸네일은 사진이 아니라 <b>동영상의 일부</b>라 순서({@code position})를
 * 가질 이유도 없다.
 *
 * <h2>🔴 이 행을 지우는 순서가 탈퇴를 살린다</h2>
 *
 * 두 외래키에 {@code ON DELETE} 를 두지 않았다({@link StoryImage} 와 같다). 그래서 탈퇴가
 * {@code uploaded_video}·{@code uploaded_image} 를 지우기 <b>전에</b> 이 행을 먼저 지워야 한다 —
 * 순서를 틀리면 <b>사진 한 장이 아니라 탈퇴 전체가 외래키 위반으로 실패한다.</b> 조용히
 * 통과하는 것보다 낫다고 보고 그렇게 두었다.
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
	 * <p>🔴 <b>없을 수 있다</b>(S15P21E201-1279). 앱이 썸네일을 못 만들면 {@code null} 이고, 그것이
	 * <b>정상 상태</b>다 — 「동영상은 올라갔는데 썸네일만 없다」. 필수로 두면 그 동영상을 아예
	 * 저장할 수 없다.
	 *
	 * <p>🔴 <b>이 칸이 {@code null} 이면 지우는 쪽이 건너뛰어야 한다.</b>
	 * {@code StoryService.deleteVideoFiles} 가 그렇게 한다 — 안 그러면 기록 삭제가 통째로 죽는다.
	 */
	@Column(name = "thumbnail_upload_id", nullable = false, updatable = false)
	private UUID thumbnailUploadId;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected StoryVideo() {
	}

	public StoryVideo(UUID storyVideoId, UUID storyId, UUID uploadedVideoId, UUID thumbnailUploadId,
			Instant createdAt) {
		// 🔴 thumbnailUploadId 는 여기 없다 — 없을 수 있는 칸이다(S15P21E201-1279).
		//    앱이 썸네일을 못 만들면 「동영상은 있고 썸네일만 없다」가 정상 상태다.
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
