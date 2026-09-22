package com.gabolle.backend.collection.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 컬렉션에 담긴 것 하나. 종류가 둘이다.
 *
 * <ul>
 *   <li>{@link Kind#PLACE} — 장소 표를 가리킨다. 이름·좌표·사진을 그 장소에서 가져오고 여기에
 *       베껴 적지 않는다 — 베끼면 장소 이름이 바뀌었을 때 한쪽만 낡는다</li>
 *   <li>{@link Kind#CUSTOM} — 사용자가 직접 적었다. 우리 목록에 없는 곳이라 이름·메모·사진을
 *       이 행이 직접 갖는다</li>
 * </ul>
 *
 * <p>종류는 값으로 갖고 {@code placeId} 가 비었는지로 추론하지 않는다. 그렇게 두면 가리키던
 * 장소가 지워진 행과 직접 적은 행이 구분되지 않는다.
 */
@Entity
@Table(name = "collection_item")
public class CollectionItem {

	/**
	 * 아래 넷은 각각 이 클래스의 {@code @Column(length)} 및 마이그레이션의 {@code varchar} 폭과
	 * 같은 값이다. 한쪽만 고치면 그 순간부터 DB 가 거부할 값을 우리가 통과시키거나(→500),
	 * DB 가 받아 줄 값을 우리가 거절한다. {@code CollectionController} 의 {@code @Size} 도
	 * 이 상수들을 읽는다.
	 */
	public static final int NAME_MAX_LENGTH = 200;

	public static final int LOCALITY_MAX_LENGTH = 100;

	public static final int PHOTO_URL_MAX_LENGTH = 500;

	public static final int NOTE_MAX_LENGTH = 500;

	@Id
	@Column(name = "collection_item_id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "collection_id", nullable = false, updatable = false)
	private UUID collectionId;

	@Enumerated(EnumType.STRING)
	@Column(name = "kind", nullable = false, length = 20, updatable = false)
	private Kind kind;

	@Column(name = "place_id", updatable = false)
	private UUID placeId;

	@Column(name = "name", length = 200)
	private String name;

	@Column(name = "locality", length = 100)
	private String locality;

	@Column(name = "lat")
	private Double lat;

	@Column(name = "lng")
	private Double lng;

	/**
	 * 사용자가 올린 사진의 주소. 기기 안 경로가 아니라 이미지 업로드 창구가 돌려준 주소다 —
	 * 기기 경로는 다른 기기에서 아무 의미가 없다.
	 */
	@Column(name = "photo_url", length = 500)
	private String photoUrl;

	@Column(name = "note", length = 500)
	private String note;

	@Column(name = "position", nullable = false)
	private int position;

	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected CollectionItem() {
		// JPA 전용
	}

	/** 우리가 아는 곳을 담는다. 이름·좌표·사진은 그 장소에서 오므로 여기 안 적는다. */
	public static CollectionItem ofPlace(UUID id, UUID collectionId, UUID placeId, String note, int position,
			OffsetDateTime now) {
		if (placeId == null) {
			throw new IllegalArgumentException("장소 항목에는 placeId 가 있어야 한다");
		}
		CollectionItem item = new CollectionItem();
		item.id = id;
		item.collectionId = collectionId;
		item.kind = Kind.PLACE;
		item.placeId = placeId;
		item.note = noteOrNull(note);
		item.position = position;
		item.createdAt = now;
		item.updatedAt = now;
		return item;
	}

	/** 사용자가 직접 적은 것을 담는다. 이름만 필수다. */
	public static CollectionItem ofCustom(UUID id, UUID collectionId, String name, String locality, Double lat,
			Double lng, String photoUrl, String note, int position, OffsetDateTime now) {
		CollectionItem item = new CollectionItem();
		item.id = id;
		item.collectionId = collectionId;
		item.kind = Kind.CUSTOM;
		item.name = requireName(name);
		item.locality = localityOrNull(locality);
		item.lat = lat;
		item.lng = lng;
		item.photoUrl = photoUrlOrNull(photoUrl);
		item.note = noteOrNull(note);
		item.position = position;
		item.createdAt = now;
		item.updatedAt = now;
		return item;
	}

	/**
	 * 메모와 차례를 고친다. 종류와 가리키는 장소는 바꾸지 않는다 — 필요하면 지우고 새로
	 * 담아야 만든 시각이 실제와 맞는다.
	 */
	public void edit(String note, Integer position, OffsetDateTime now) {
		this.note = noteOrNull(note);
		if (position != null) {
			this.position = position;
		}
		this.updatedAt = now;
	}

	/** 직접 적은 항목의 내용을 고친다. 장소 항목에는 쓸 수 없다 — 그 값들은 장소 것이다. */
	public void editCustom(String name, String locality, Double lat, Double lng, String photoUrl, String note,
			Integer position, OffsetDateTime now) {
		if (this.kind != Kind.CUSTOM) {
			throw new IllegalStateException(
					"장소 항목의 이름·좌표·사진은 여기서 못 고친다 — 그 값들은 장소 표의 것이다");
		}
		this.name = requireName(name);
		this.locality = localityOrNull(locality);
		this.lat = lat;
		this.lng = lng;
		this.photoUrl = photoUrlOrNull(photoUrl);
		edit(note, position, now);
	}

	/**
	 * 아래 넷은 비었는지와 길이를 함께 본다. 길이를 안 보면 열 폭을 넘는 값이 자바 검사를
	 * 전부 통과한 뒤 PostgreSQL 에서 거부돼 500 으로 나간다.
	 */
	private static String requireName(String name) {
		String trimmed = (name == null) ? "" : name.trim();
		if (trimmed.isEmpty()) {
			throw new IllegalArgumentException("직접 적은 항목에는 이름이 있어야 한다");
		}
		return TextFields.requiredLine(trimmed, "항목 이름", NAME_MAX_LENGTH);
	}

	private static String localityOrNull(String value) {
		return TextFields.optionalLine(value, "지역", LOCALITY_MAX_LENGTH);
	}

	private static String photoUrlOrNull(String value) {
		return TextFields.optionalLine(value, "사진 주소", PHOTO_URL_MAX_LENGTH);
	}

	private static String noteOrNull(String value) {
		return TextFields.optionalText(value, "메모", NOTE_MAX_LENGTH);
	}

	/**
	 * 메모를 이 엔티티와 똑같이 다듬어 돌려준다. 장소 항목은 업서트 한 문장으로 들어가
	 * 엔티티를 거치지 않으므로, 같은 규칙을 두 벌로 적지 않고 이 자리를 열어 준다.
	 */
	public static String normalizedNote(String value) {
		return noteOrNull(value);
	}

	public UUID getId() {
		return this.id;
	}

	public UUID getCollectionId() {
		return this.collectionId;
	}

	public Kind getKind() {
		return this.kind;
	}

	public UUID getPlaceId() {
		return this.placeId;
	}

	public String getName() {
		return this.name;
	}

	public String getLocality() {
		return this.locality;
	}

	public Double getLat() {
		return this.lat;
	}

	public Double getLng() {
		return this.lng;
	}

	public String getPhotoUrl() {
		return this.photoUrl;
	}

	public String getNote() {
		return this.note;
	}

	public int getPosition() {
		return this.position;
	}

	public OffsetDateTime getCreatedAt() {
		return this.createdAt;
	}

	public OffsetDateTime getUpdatedAt() {
		return this.updatedAt;
	}

	/** 이 항목이 무엇인가. {@code placeId} 가 비었는지로 추론하지 않는다. */
	public enum Kind {

		/** 장소 표를 가리킨다. */
		PLACE,

		/** 사용자가 직접 적었다. */
		CUSTOM
	}
}
