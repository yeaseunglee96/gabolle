package com.gabolle.backend.place.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 사용자가 저장한(하트) 장소 — S15P21E201-1013.
 *
 * <p>지금까지 기기에만 있었다. 기기를 바꾸면 사라졌다.
 *
 * <p>🔴 {@code RecommendationPlaceAction} 과 <b>다른 것이다</b> — 이름이 비슷해 합치기
 * 쉬운 자리라 여기 적어 둔다.
 * <ul>
 *   <li>{@code RecommendationPlaceAction} — <b>"이번 여행의 후보로"</b> 담아둔다/뺀다.
 *       여행에 매달려 있고 <b>동행자가 함께 본다</b></li>
 *   <li>{@code SavedPlace}(이 클래스) — <b>"이 장소가 마음에 든다"</b>. 여행과 무관한
 *       전역 목록이고 <b>그 사람만의 것</b>이다</li>
 * </ul>
 * 합치면 "다른 여행에서 담아둔 것이 홈 하트에 뜨는" 일이 생긴다.
 */
@Entity
@Table(name = "saved_place")
public class SavedPlace {

	@Id
	@Column(name = "saved_place_id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(name = "place_id", nullable = false, updatable = false)
	private UUID placeId;

	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	protected SavedPlace() {
		// JPA 전용
	}

	private SavedPlace(UUID id, UUID userId, UUID placeId, OffsetDateTime createdAt) {
		this.id = id;
		this.userId = userId;
		this.placeId = placeId;
		this.createdAt = createdAt;
	}

	public static SavedPlace of(UUID id, UUID userId, UUID placeId, OffsetDateTime createdAt) {
		return new SavedPlace(id, userId, placeId, createdAt);
	}

	public UUID getId() {
		return this.id;
	}

	public UUID getUserId() {
		return this.userId;
	}

	public UUID getPlaceId() {
		return this.placeId;
	}

	public OffsetDateTime getCreatedAt() {
		return this.createdAt;
	}
}
