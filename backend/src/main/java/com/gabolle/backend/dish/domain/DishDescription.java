package com.gabolle.backend.dish.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 음식 하나에 대한 한 줄 설명.
 *
 * <p>사진에서 읽은 값이 아니라 모델이 아는 것을 말한 값이다. 나머지 메뉴판 응답과 달리 틀릴 수 있고,
 * 그 식당이 실제로 무엇을 넣는지는 여기 들어 있지 않다. 알레르기 판단에 이 값을 쓰지 않는다 — 그
 * 통로는 {@code allergenWords} 하나뿐이다.
 */
@Entity
@Table(name = "dish_description")
public class DishDescription {

	@Id
	@Column(name = "dish_description_id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "name_key", nullable = false, updatable = false)
	private String nameKey;

	@Column(name = "language", nullable = false, updatable = false)
	private String language;

	@Column(name = "description", nullable = false, updatable = false)
	private String description;

	/** 그림을 그릴 때 쓰는 영어 묘사. 설명과 같은 호출에서 함께 받아 둔다. */
	@Column(name = "image_prompt", nullable = false, updatable = false)
	private String imagePrompt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	protected DishDescription() {
		// JPA 전용
	}

	private DishDescription(UUID id, String nameKey, String language, String description,
			String imagePrompt, OffsetDateTime createdAt) {
		this.id = id;
		this.nameKey = nameKey;
		this.language = language;
		this.description = description;
		this.imagePrompt = imagePrompt;
		this.createdAt = createdAt;
	}

	public static DishDescription of(UUID id, String nameKey, String language, String description,
			String imagePrompt, OffsetDateTime createdAt) {
		return new DishDescription(id, nameKey, language, description, imagePrompt, createdAt);
	}

	public UUID getId() {
		return this.id;
	}

	public String getNameKey() {
		return this.nameKey;
	}

	public String getLanguage() {
		return this.language;
	}

	public String getDescription() {
		return this.description;
	}

	public String getImagePrompt() {
		return this.imagePrompt;
	}

	public OffsetDateTime getCreatedAt() {
		return this.createdAt;
	}
}
