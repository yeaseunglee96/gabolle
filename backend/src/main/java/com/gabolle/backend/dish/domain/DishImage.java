package com.gabolle.backend.dish.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 음식 하나에 대해 만들어 둔 그림.
 *
 * <p>그 식당의 음식 사진이 아니라 이름만 보고 모델이 그린 그림이다. 실제로 나오는 음식과 대개
 * 다르므로 화면은 그 사실을 그림에 붙여서 말해야 한다. 알레르기 판단에 쓰지 않는다 — 그림에 새우가
 * 안 보이는 것은 아무 뜻이 없다.
 *
 * <p>이 한 행이 저장소이자 진행 상태다. 진행 상태를 다른 표에 두면 «행은 없는데 작업은 도는 중»
 * 같은 사이 상태가 생긴다. 한 행으로 두면 {@code UNIQUE(name_key)} 가 «같은 음식을 두 번 만들지
 * 않는다»를 데이터베이스가 대신 지켜 준다.
 */
@Entity
@Table(name = "dish_image")
public class DishImage {

	/** 만드는 중. 화면은 조금 뒤에 다시 물어본다. */
	public static final String PENDING = "PENDING";

	/** 그림이 있다. */
	public static final String READY = "READY";

	/** 못 만들었다. «그림이 없는 음식»이 아니라 «이번에 못 만들었다»다. */
	public static final String FAILED = "FAILED";

	@Id
	@Column(name = "dish_image_id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "name_key", nullable = false, updatable = false)
	private String nameKey;

	@Column(name = "status", nullable = false)
	private String status;

	@Column(name = "image_bytes")
	private byte[] imageBytes;

	@Column(name = "content_type")
	private String contentType;

	@Column(name = "failure_note")
	private String failureNote;

	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected DishImage() {
		// JPA 전용
	}

	private DishImage(UUID id, String nameKey, OffsetDateTime now) {
		this.id = id;
		this.nameKey = nameKey;
		this.status = PENDING;
		this.createdAt = now;
		this.updatedAt = now;
	}

	/** 아직 그림이 없는 자리를 잡는다. 이 행을 저장하는 데 성공한 쪽만 실제로 만든다. */
	public static DishImage pending(UUID id, String nameKey, OffsetDateTime now) {
		return new DishImage(id, nameKey, now);
	}

	public void markReady(byte[] bytes, String contentType, OffsetDateTime now) {
		this.status = READY;
		this.imageBytes = bytes;
		this.contentType = contentType;
		this.failureNote = null;
		this.updatedAt = now;
	}

	/**
	 * 실패를 지우지 않고 적는다. 행을 지우면 다음 사람이 누를 때 또 만들려 들고, 그 음식이 원래
	 * 안 되는 것이면 값만 계속 나간다.
	 */
	public void markFailed(String note, OffsetDateTime now) {
		this.status = FAILED;
		this.imageBytes = null;
		this.contentType = null;
		this.failureNote = note;
		this.updatedAt = now;
	}

	/** 다시 만들어 볼 수 있게 되돌린다 — 실패한 지 한참 지난 행에만 쓴다. */
	public void markPendingAgain(OffsetDateTime now) {
		this.status = PENDING;
		this.failureNote = null;
		this.updatedAt = now;
	}

	public UUID getId() {
		return this.id;
	}

	public String getNameKey() {
		return this.nameKey;
	}

	public String getStatus() {
		return this.status;
	}

	public byte[] getImageBytes() {
		return this.imageBytes;
	}

	public String getContentType() {
		return this.contentType;
	}

	public String getFailureNote() {
		return this.failureNote;
	}

	public OffsetDateTime getCreatedAt() {
		return this.createdAt;
	}

	public OffsetDateTime getUpdatedAt() {
		return this.updatedAt;
	}
}
