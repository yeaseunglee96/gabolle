package com.gabolle.backend.dish.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 그림을 새로 만들기 시작한 기록. 이미 만들어 둔 그림을 꺼내 쓰는 것은 여기 안 적는다.
 *
 * <p>메뉴판 읽기 기록({@code menu_scan_usage})과 표를 나눠 둔다. 섞으면 그림 몇 장이 그날의 메뉴판
 * 읽기까지 막는데, 읽기는 알레르기 낱말을 보는 자리라 그쪽이 먼저 막히면 안 된다.
 */
@Entity
@Table(name = "dish_image_usage")
public class DishImageUsage {

	@Id
	@Column(name = "dish_image_usage_id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(name = "requested_at", nullable = false, updatable = false)
	private OffsetDateTime requestedAt;

	protected DishImageUsage() {
		// JPA 전용
	}

	private DishImageUsage(UUID id, UUID userId, OffsetDateTime requestedAt) {
		this.id = id;
		this.userId = userId;
		this.requestedAt = requestedAt;
	}

	public static DishImageUsage of(UUID id, UUID userId, OffsetDateTime requestedAt) {
		return new DishImageUsage(id, userId, requestedAt);
	}

	public UUID getId() {
		return this.id;
	}

	public UUID getUserId() {
		return this.userId;
	}

	public OffsetDateTime getRequestedAt() {
		return this.requestedAt;
	}
}
