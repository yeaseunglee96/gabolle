package com.gabolle.backend.editorial.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * {@link EditorialPickPlace} 의 복합 키 — {@code (pick_id, place_id)}
 * ({@code pk_editorial_pick_place}).
 *
 * <p> 키에 {@code pickRank} 를 넣지 않는다. 순위는 바뀔 수 있는 값이고, 키에 넣으면 순서를
 * 고치는 것이 행을 지우고 새로 만드는 일이 된다. 같은 Pick 에 같은 장소가 두 번 들어가지
 * 않게 막는 것이 이 키의 목적이다 — 순위가 겹치지 않는 것은
 * {@code uq_editorial_pick_place_rank} 가 따로 본다.
 */
@Embeddable
public class EditorialPickPlaceId implements Serializable {

	private static final long serialVersionUID = 1L;

	@Column(name = "pick_id", nullable = false, updatable = false)
	private UUID pickId;

	@Column(name = "place_id", nullable = false, updatable = false)
	private UUID placeId;

	protected EditorialPickPlaceId() {
		// JPA 전용.
	}

	public UUID getPickId() {
		return this.pickId;
	}

	public UUID getPlaceId() {
		return this.placeId;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof EditorialPickPlaceId that)) {
			return false;
		}
		return Objects.equals(this.pickId, that.pickId) && Objects.equals(this.placeId, that.placeId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.pickId, this.placeId);
	}
}
