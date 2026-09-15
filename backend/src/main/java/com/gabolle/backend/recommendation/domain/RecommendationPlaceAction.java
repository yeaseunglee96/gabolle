package com.gabolle.backend.recommendation.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 추천 후보 하나에 대한 사용자의 판단 — S15P21E201-1013.
 *
 * <p>🔴 <b>"이 장소가 좋다" 가 아니라 "이번 여행의 후보로 좋다" 이다.</b> 그래서 여행 번호가
 * 키에 들어간다. 장소 하나에 판단 하나를 두면, 다른 여행에서 뺀 장소가 이번 여행에서도
 * 빠진 것처럼 보인다.
 *
 * <p>🔴 {@code itinerary_excluded_place} 와 다르다. 그쪽은 <b>이미 만들어진 일정에서</b> 뺀
 * 장소라 일정 판에 매달려 있고, 이쪽은 <b>일정이 아직 없어도</b> 생긴다.
 */
@Entity
@Table(name = "recommendation_place_action")
public class RecommendationPlaceAction {

	@Id
	@Column(name = "recommendation_place_action_id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(name = "trip_id", nullable = false, updatable = false)
	private UUID tripId;

	@Column(name = "place_id", nullable = false, updatable = false)
	private UUID placeId;

	@Enumerated(EnumType.STRING)
	@Column(name = "action", nullable = false, length = 20)
	private Action action;

	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected RecommendationPlaceAction() {
		// JPA 전용
	}

	private RecommendationPlaceAction(UUID id, UUID userId, UUID tripId, UUID placeId, Action action,
			OffsetDateTime now) {
		this.id = id;
		this.userId = userId;
		this.tripId = tripId;
		this.placeId = placeId;
		this.action = action;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public static RecommendationPlaceAction of(UUID id, UUID userId, UUID tripId, UUID placeId, Action action,
			OffsetDateTime now) {
		if (action == null) {
			throw new IllegalArgumentException("action 은 필수다 — 판단 없는 행은 뜻이 없다");
		}
		return new RecommendationPlaceAction(id, userId, tripId, placeId, action, now);
	}

	/**
	 * 같은 장소에 대한 판단을 바꾼다 (담아둠 ↔ 뺌).
	 *
	 * <p>새 행을 만들지 않는 이유는 {@code uk_recommendation_place_action} 이 한 사람·한
	 * 여행·한 장소에 행 하나만 허용하기 때문이다. 둘을 허용하면 같은 장소가 담김이면서
	 * 동시에 빠진 상태가 되고, 화면은 둘 중 아무거나 그린다.
	 */
	public void changeTo(Action action, OffsetDateTime now) {
		if (action == null) {
			throw new IllegalArgumentException("action 은 필수다 — 판단 없는 행은 뜻이 없다");
		}
		this.action = action;
		this.updatedAt = now;
	}

	public UUID getId() {
		return this.id;
	}

	public UUID getUserId() {
		return this.userId;
	}

	public UUID getTripId() {
		return this.tripId;
	}

	public UUID getPlaceId() {
		return this.placeId;
	}

	public Action getAction() {
		return this.action;
	}

	public OffsetDateTime getCreatedAt() {
		return this.createdAt;
	}

	public OffsetDateTime getUpdatedAt() {
		return this.updatedAt;
	}

	/** 화면의 하트(담아두기)와 가리기(빼기)에 그대로 대응한다. */
	public enum Action {

		SAVED, EXCLUDED
	}
}
