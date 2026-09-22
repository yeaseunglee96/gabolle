package com.gabolle.backend.story.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 저장소에 올라간 동영상 한 개 — 주소와 저장 키만 있다. 파일 내용은 여기 없다. 올린 사람을
 * 기억해서 남이 올린 주소를 내 기록에 붙이는 것을 막고, {@code deletedAt} 은 저장소에서 파일을
 * 지운 시각이다.
 *
 * <p>{@link UploadedImage} 와 모양이 같지만 표를 나눴다. 그쪽은 도메인에서 3MB 를 강제하고
 * ({@code MAX_BYTES}) 칸 이름도 {@code image_url} 이다.
 *
 * <p>크기 상한은 설정값({@code gabolle.storage.video.max-bytes})이라 여기 없다. 상한 판정은
 * {@code VideoUploadService} 가 하고 여기서는 0보다 큰가만 본다 — 표의
 * {@code ck_uploaded_video_byte_size} 와 같다.
 *
 * <p>{@code durationSec} 은 앱이 잰 값이다. 서버는 동영상 파일을 열지 않으므로 확인하지 않고,
 * 안 주면 {@code null} 이다.
 */
@Entity
@Table(name = "uploaded_video")
public class UploadedVideo {

	@Id
	@Column(name = "uploaded_video_id", nullable = false, updatable = false)
	private UUID uploadedVideoId;

	@Column(name = "uploader_user_id", nullable = false, updatable = false)
	private UUID uploaderUserId;

	@Column(name = "storage_key", nullable = false, updatable = false, length = 300)
	private String storageKey;

	@Column(name = "video_url", nullable = false, length = 500)
	private String videoUrl;

	@Column(name = "content_type", nullable = false, length = 50)
	private String contentType;

	@Column(name = "byte_size", nullable = false)
	private long byteSize;

	@Column(name = "duration_sec")
	private Integer durationSec;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	protected UploadedVideo() {
	}

	public UploadedVideo(UUID uploadedVideoId, UUID uploaderUserId, String storageKey, String videoUrl,
			String contentType, long byteSize, Integer durationSec, Instant createdAt) {
		if (uploadedVideoId == null || uploaderUserId == null || storageKey == null || videoUrl == null
				|| contentType == null || createdAt == null) {
			throw new IllegalArgumentException(
					"uploadedVideoId·uploaderUserId·storageKey·videoUrl·contentType·createdAt 는 필수다");
		}
		if (byteSize <= 0) {
			throw new IllegalArgumentException("동영상 크기는 1 바이트 이상이어야 한다: " + byteSize);
		}
		if (durationSec != null && durationSec <= 0) {
			throw new IllegalArgumentException("길이는 1초 이상이거나 없어야 한다: " + durationSec);
		}
		this.uploadedVideoId = uploadedVideoId;
		this.uploaderUserId = uploaderUserId;
		this.storageKey = storageKey;
		this.videoUrl = videoUrl;
		this.contentType = contentType;
		this.byteSize = byteSize;
		this.durationSec = durationSec;
		this.createdAt = createdAt;
	}

	public void markDeleted(Instant now) {
		this.deletedAt = now;
	}

	public boolean isDeleted() {
		return deletedAt != null;
	}

	public boolean isOwnedBy(UUID userId) {
		return uploaderUserId.equals(userId);
	}

	public UUID getUploadedVideoId() { return uploadedVideoId; }
	public UUID getUploaderUserId()  { return uploaderUserId; }
	public String getStorageKey()    { return storageKey; }
	public String getVideoUrl()      { return videoUrl; }
	public String getContentType()   { return contentType; }
	public long getByteSize()        { return byteSize; }
	public Integer getDurationSec()  { return durationSec; }
	public Instant getCreatedAt()    { return createdAt; }
	public Instant getDeletedAt()    { return deletedAt; }
}
