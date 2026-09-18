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
 * 추천 후보 하나에 대한 판단 — S15P21E201-1013.
 *
 * <p>🔴 <b>"이 장소가 좋다" 가 아니라 "이번 여행의 후보로 좋다" 이다.</b> 그래서 여행 번호가
 * 키에 들어간다. 장소 하나에 판단 하나를 두면, 다른 여행에서 뺀 장소가 이번 여행에서도
 * 빠진 것처럼 보인다.
 *
 * <p>🔴 <b>사람별이 아니라 여행별이다 — 동행자가 함께 본다</b> (2026-09-15 제품 결정).
 * "같이 일정짜기" 를 따로 만들지 않고 이미 있는 <b>여행 초대</b>를 공유 장치로 쓴다.
 * 여행이 곧 공유 단위이고 사람은 초대로 들어오므로, 그 안의 판단도 함께 보는 것이 맞다.
 * 덕분에 권한 규칙이 하나로 줄어든다 — <b>그 여행의 참여자인가</b>만 물으면 된다.
 *
 * <p>{@link #decidedByUserId} 는 <b>키가 아니라 기록</b>이다. 공유 상태에서 "이 후보가 왜
 * 사라졌지" 에 답할 수 없으면 동행자끼리 서로를 의심하게 된다.
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
	 * 같은 장소에 대한 판단을 바꾼다 (담아둠 ↔ 뺌).
	 *
	 * <p>새 행을 만들지 않는 이유는 {@code uk_recommendation_place_action} 이 한 여행·한
	 * 장소에 행 하나만 허용하기 때문이다. 둘을 허용하면 같은 장소가 담김이면서 동시에 빠진
	 * 상태가 되고, 화면은 둘 중 아무거나 그린다.
	 *
	 * <p>바꾼 사람을 함께 적는다 — 동행자가 함께 보는 값이라 <b>마지막에 누가 정했는지</b>가
	 * 곧 그 판단의 설명이 된다.
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
