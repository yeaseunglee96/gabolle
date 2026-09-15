package com.gabolle.backend.collection.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 사용자가 만든 장소 묶음 — S15P21E201-1013.
 *
 * <p>지금까지 기기에만 있었다. 화면도 「이 기기에만 저장돼요. 앱을 지우면 사라져요」라고
 * 정직하게 말하고 있었다.
 *
 * <p>🔴 {@code SavedPlace}(하트)와 다르다. 하트는 <b>한 덩어리의 목록 하나</b>이고, 컬렉션은
 * 사용자가 <b>이름을 붙여 여러 개</b> 만든다. 그리고 컬렉션에는 <b>우리 목록에 없는 곳</b>도
 * 들어간다 — 그 차이가 {@link CollectionItem} 의 종류를 만든다.
 */
@Entity
@Table(name = "collection")
public class Collection {

	@Id
	@Column(name = "collection_id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(name = "name", nullable = false, length = 100)
	private String name;

	@Column(name = "description", length = 500)
	private String description;

	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected Collection() {
		// JPA 전용
	}

	private Collection(UUID id, UUID userId, String name, String description, OffsetDateTime now) {
		this.id = id;
		this.userId = userId;
		this.name = name;
		this.description = description;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public static Collection of(UUID id, UUID userId, String name, String description, OffsetDateTime now) {
		return new Collection(id, userId, requireName(name), blankToNull(description), now);
	}

	public void rename(String name, String description, OffsetDateTime now) {
		this.name = requireName(name);
		this.description = blankToNull(description);
		this.updatedAt = now;
	}

	/** 항목이 바뀌었을 때 목록의 «마지막 손댄 시각» 을 옮긴다 — 화면이 최근 순으로 보여 준다. */
	public void touch(OffsetDateTime now) {
		this.updatedAt = now;
	}

	/**
	 * 🔴 이름 없는 컬렉션을 만들 수 없다. 화면에서 사용자가 이름을 비우고 저장하면
	 * «제목 없음» 이 아니라 <b>거절</b>이 맞다 — 목록에 이름 없는 줄이 쌓이면 사용자가
	 * 자기 것을 못 고른다. DB 의 {@code ck_collection_name_not_blank} 와 같은 것을 여기서도 본다.
	 */
	private static String requireName(String name) {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("컬렉션 이름은 비울 수 없다");
		}
		return name.trim();
	}

	/** 빈 문자열을 {@code null} 로 맞춘다 — «안 적었다» 와 «빈 칸을 적었다» 를 같게 둔다. */
	private static String blankToNull(String value) {
		return (value == null || value.isBlank()) ? null : value.trim();
	}

	public UUID getId() {
		return this.id;
	}

	public UUID getUserId() {
		return this.userId;
	}

	public String getName() {
		return this.name;
	}

	public String getDescription() {
		return this.description;
	}

	public OffsetDateTime getCreatedAt() {
		return this.createdAt;
	}

	public OffsetDateTime getUpdatedAt() {
		return this.updatedAt;
	}
}
