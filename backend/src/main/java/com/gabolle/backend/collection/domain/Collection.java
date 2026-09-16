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

	/**
	 * 이름의 최대 글자 수. 🔴 아래 {@code @Column(length)} 및 마이그레이션의
	 * {@code varchar(100)} 과 같은 값이어야 한다 — 이 상수가 더 크면 DB 가 거부해 500 이 되고,
	 * 더 작으면 DB 가 받아 줄 이름을 우리가 먼저 거절한다. {@code CollectionController} 의
	 * {@code @Size} 도 이 상수를 읽는다.
	 */
	public static final int NAME_MAX_LENGTH = 100;

	/** 설명의 최대 글자 수. {@link #NAME_MAX_LENGTH} 와 같은 이유로 열 폭과 맞춘다. */
	public static final int DESCRIPTION_MAX_LENGTH = 500;

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
	 *
	 * <p>🔴 2026-09-16 (S15P21E201-1037) — 길이도 여기서 본다. 그전에는 비었는지만 보고
	 * 넘겨서, 100자를 넘는 이름이 PostgreSQL 까지 가서 거부되고 500 으로 나갔다.
	 */
	private static String requireName(String name) {
		return TextFields.requiredLine(name, "컬렉션 이름", NAME_MAX_LENGTH);
	}

	/** 빈 문자열을 {@code null} 로 맞춘다 — «안 적었다» 와 «빈 칸을 적었다» 를 같게 둔다. */
	private static String blankToNull(String value) {
		return TextFields.optionalText(value, "컬렉션 설명", DESCRIPTION_MAX_LENGTH);
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
