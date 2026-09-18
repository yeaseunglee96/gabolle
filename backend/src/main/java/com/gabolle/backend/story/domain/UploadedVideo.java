package com.gabolle.backend.story.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 저장소에 올라간 동영상 한 개 — 주소와 저장 키만 있다. 파일 내용은 여기 없다. S15P21E201-1275.
 *
 * <p>{@link UploadedImage} 와 <b>같은 모양</b>이다. 올린 사람을 기억해서 남이 올린 주소를 내
 * 기록에 붙이는 것을 막고, {@code deletedAt} 은 저장소에서 파일을 지운 시각이다.
 *
 * <h2>🔴 {@link UploadedImage} 와 표를 나눈 이유</h2>
 *
 * 모양이 같은데도 합치지 않았다. {@code UploadedImage} 는 <b>도메인에서 3MB 를 강제</b>한다
 * ({@code MAX_BYTES}). 동영상을 거기 담으려면 그 검증을 풀어야 하고, 그러면 <b>사진 길이 같이
 * 바뀐다.</b> 이름도 {@code image_url} 이라 거짓이 된다.
 *
 * <h2>🔴 크기 상한이 여기 없다</h2>
 *
 * {@code UploadedImage} 는 3MB 를 상수로 들고 있지만 이쪽은 <b>설정값</b>이다
 * ({@code gabolle.storage.video.max-bytes}). 값이 아직 실측 전이고, 정해진 뒤에도 코드를
 * 고치지 않고 바꿀 수 있어야 한다. 그래서 상한 판정은 {@code VideoUploadService} 가 하고
 * 여기서는 <b>0보다 큰가</b>만 본다 — 표의 {@code ck_uploaded_video_byte_size} 와 같다.
 *
 * <h2>🔴 {@code durationSec} 은 앱이 잰 것이다</h2>
 *
 * 서버는 동영상 파일을 <b>열지 않는다</b>(2026-09-18 결정 — 줄이기·썸네일·길이 재기를 전부 앱이
 * 한다. 서버에 동영상 디코더를 들이지 않는다). 그래서 이 값은 <b>앱이 말해 준 것</b>이고 서버가
 * 확인하지 않는다. 안 주면 {@code null} 이다.
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
