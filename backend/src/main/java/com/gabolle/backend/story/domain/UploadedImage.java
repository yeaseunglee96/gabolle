package com.gabolle.backend.story.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 파일 저장소에 올라간 사진 한 장 — 주소와 저장 키만 있다. 사진 데이터는 여기 없다.
 *
 * <p>업로드와 기록 저장은 두 요청이다. 먼저 사진을 올려 주소를 받고 그 주소로 기록을 만든다. 그 사이에
 * 이 행이 "올린 사람의 것" 임을 기억해서, 남이 올린 사진 주소를 내 기록에 붙이는 것을 막는다.
 *
 * <p>{@code deletedAt} 은 저장소에서 파일을 지운 시각이다. 기록을 지우면 함께 찍힌다.
 */
@Entity
@Table(name = "uploaded_image")
public class UploadedImage {

	public static final int MAX_BYTES = 3 * 1024 * 1024;

	@Id
	@Column(name = "uploaded_image_id", nullable = false, updatable = false)
	private UUID uploadedImageId;

	@Column(name = "uploader_user_id", nullable = false, updatable = false)
	private UUID uploaderUserId;

	@Column(name = "storage_key", nullable = false, updatable = false, length = 300)
	private String storageKey;

	@Column(name = "image_url", nullable = false, length = 500)
	private String imageUrl;

	@Column(name = "content_type", nullable = false, length = 50)
	private String contentType;

	@Column(name = "byte_size", nullable = false)
	private int byteSize;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	protected UploadedImage() {
	}

	public UploadedImage(UUID uploadedImageId, UUID uploaderUserId, String storageKey, String imageUrl,
			String contentType, int byteSize, Instant createdAt) {
		if (uploadedImageId == null || uploaderUserId == null || storageKey == null || imageUrl == null
				|| contentType == null || createdAt == null) {
			throw new IllegalArgumentException("uploadedImageId·uploaderUserId·storageKey·imageUrl·contentType·createdAt 는 필수다");
		}
		if (byteSize <= 0 || byteSize > MAX_BYTES) {
			throw new IllegalArgumentException("사진 크기는 1 바이트 이상 3MB 이하여야 한다: " + byteSize);
		}
		this.uploadedImageId = uploadedImageId;
		this.uploaderUserId = uploaderUserId;
		this.storageKey = storageKey;
		this.imageUrl = imageUrl;
		this.contentType = contentType;
		this.byteSize = byteSize;
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

	public UUID getUploadedImageId() { return uploadedImageId; }
	public UUID getUploaderUserId()  { return uploaderUserId; }
	public String getStorageKey()    { return storageKey; }
	public String getImageUrl()      { return imageUrl; }
	public String getContentType()   { return contentType; }
	public int getByteSize()         { return byteSize; }
	public Instant getCreatedAt()    { return createdAt; }
	public Instant getDeletedAt()    { return deletedAt; }
}
