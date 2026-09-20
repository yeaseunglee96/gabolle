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
 * 추천 후보 하나에 대한 판단. "이 장소가 좋다" 가 아니라 "이번 여행의 후보로 좋다" 라서 여행
 * 번호가 키에 들어간다 — 장소 하나에 판단 하나를 두면 다른 여행에서 뺀 장소가 이번 여행에서도
 * 빠진 것처럼 보인다.
 *
 * 사람별이 아니라 여행별이라 동행자가 함께 본다. 권한 판단은 그 여행의 참여자인가 하나로
 * 끝난다. {@link #decidedByUserId} 는 키가 아니라 기록이다.
 *
 * {@code itinerary_excluded_place} 와 다르다 — 그쪽은 이미 만들어진 일정에서 뺀 장소라 일정
 * 판에 매달려 있고, 이쪽은 일정이 아직 없어도 생긴다.
 */
@Entity
@Table(name = "recommendation_place_action")
public class RecommendationPlaceAction {

	@Id
	@Column(name = "recommendation_place_action_id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "trip_id", nullable = false, updatable = false)
	private UUID tripId;

	@Column(name = "place_id", nullable = false, updatable = false)
	private UUID placeId;

	@Enumerated(EnumType.STRING)
	@Column(name = "action", nullable = false, length = 20)
	private Action action;

	/**
	 * 마지막으로 이 판단을 정한 사람. 그 사람이 계정을 지우면 {@code null} 이 된다 —
	 * 판단은 여행의 것이지 그 사람의 것이 아니므로 판단 자체는 남는다.
	 */
	@Column(name = "decided_by_user_id")
	private UUID decidedByUserId;

	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected RecommendationPlaceAction() {
		// JPA 전용
	}

	private RecommendationPlaceAction(UUID id, UUID tripId, UUID placeId, Action action, UUID decidedByUserId,
			OffsetDateTime now) {
		this.id = id;
		this.tripId = tripId;
		this.placeId = placeId;
		this.action = action;
		this.decidedByUserId = decidedByUserId;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public static RecommendationPlaceAction of(UUID id, UUID tripId, UUID placeId, Action action,
			UUID decidedByUserId, OffsetDateTime now) {
		if (action == null) {
			throw new IllegalArgumentException("action 은 필수다 — 판단 없는 행은 뜻이 없다");
		}
		return new RecommendationPlaceAction(id, tripId, placeId, action, decidedByUserId, now);
	}

	/**
	 * 같은 장소에 대한 판단을 바꾼다 (담아둠 ↔ 뺌). 새 행을 만들지 않는 것은
	 * {@code uk_recommendation_place_action} 이 한 여행·한 장소에 행 하나만 허용하기
	 * 때문이다 — 둘을 허용하면 같은 장소가 담김이면서 동시에 빠진 상태가 된다.
	 */
	public void changeTo(Action action, UUID decidedByUserId, OffsetDateTime now) {
		if (action == null) {
			throw new IllegalArgumentException("action 은 필수다 — 판단 없는 행은 뜻이 없다");
		}
		this.action = action;
		this.decidedByUserId = decidedByUserId;
		this.updatedAt = now;
	}

	public UUID getId() {
		return this.id;
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

	public UUID getDecidedByUserId() {
		return this.decidedByUserId;
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
