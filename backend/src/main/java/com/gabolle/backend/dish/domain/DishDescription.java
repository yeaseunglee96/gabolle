package com.gabolle.backend.dish.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 음식 하나에 대한 한 줄 설명 — S15P21E201-1272.
 *
 * <h2>🔴 이것은 사진에서 읽은 값이 아니다</h2>
 *
 * 나머지 메뉴판 응답({@code text}·{@code name}·{@code price}·{@code allergenWords})은
 * 전부 <b>사진에 보이는 것</b>이다. 이 칸만 다르다 — 모델이 <b>아는 것</b>을 말한 값이다.
 * 그래서 틀릴 수 있고, 그 식당이 실제로 무엇을 넣는지는 여기에 들어 있지 않다.
 *
 * <p>화면은 이 둘을 같은 무게로 그리면 안 된다. 그리고 <b>알레르기 판단에 이 값을
 * 쓰지 않는다</b> — 그 통로는 {@code allergenWords} 하나뿐이다.
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
