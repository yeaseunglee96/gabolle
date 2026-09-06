package com.gabolle.backend.story.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 기록에 붙은 사진 한 장 — 어느 기록의 몇 번째 자리에 어느 업로드가 있나.
 *
 * <p>사진 주소는 {@link UploadedImage} 에 있고 여기는 가리키기만 한다. 한 업로드는 한 기록에만
 * 붙는다({@code uq_story_image_upload}) — 두 기록이 한 파일을 나눠 쓰면 한쪽을 지울 때 다른 쪽이
 * 깨진 그림을 보게 된다.
 */
@Entity
@Table(name = "story_image")
public class StoryImage {

	@Id
	@Column(name = "story_image_id", nullable = false, updatable = false)
	private UUID storyImageId;

	@Column(name = "story_id", nullable = false, updatable = false)
	private UUID storyId;

	@Column(name = "uploaded_image_id", nullable = false, updatable = false)
	private UUID uploadedImageId;

	@Column(name = "position", nullable = false)
	private short position;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected StoryImage() {
	}

	public StoryImage(UUID storyImageId, UUID storyId, UUID uploadedImageId, int position, Instant createdAt) {
		if (position < 1 || position > Story.MAX_IMAGES) {
			throw new IllegalArgumentException("사진 자리는 1부터 " + Story.MAX_IMAGES + "까지다: " + position);
		}
		this.storyImageId = storyImageId;
		this.storyId = storyId;
		this.uploadedImageId = uploadedImageId;
		this.position = (short) position;
		this.createdAt = createdAt;
	}

	public UUID getStoryImageId()    { return storyImageId; }
	public UUID getStoryId()         { return storyId; }
	public UUID getUploadedImageId() { return uploadedImageId; }
	public int getPosition()         { return position; }
	public Instant getCreatedAt()    { return createdAt; }
}
