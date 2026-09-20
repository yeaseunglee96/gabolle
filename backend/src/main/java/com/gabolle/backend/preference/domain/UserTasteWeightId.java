package com.gabolle.backend.preference.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/** {@link UserTasteWeight} 의 복합 키 — (판, 차원, 코드). */
@Embeddable
public class UserTasteWeightId implements Serializable {

	private static final long serialVersionUID = 1L;

	@Column(name = "taste_vector_id", nullable = false, updatable = false)
	private UUID tasteVectorId;

	@Enumerated(EnumType.STRING)
	@Column(name = "dimension", nullable = false, length = 50, updatable = false)
	private TasteDimension dimension;

	/**
	 * 차원 안의 값. 예: {@code dimension=CATEGORY} 일 때 {@code code=CAFE}. 코드 목록은 화면
	 * 옵션과 장소 태그 온톨로지가 확정된 뒤에 고정한다 — 그전까지는 DB 도 막지 않는다.
	 */
	@Column(name = "code", nullable = false, length = 50, updatable = false)
	private String code;

	protected UserTasteWeightId() {
	}

	public UserTasteWeightId(UUID tasteVectorId, TasteDimension dimension, String code) {
		this.tasteVectorId = tasteVectorId;
		this.dimension = dimension;
		this.code = code;
	}

	public UUID getTasteVectorId() {
		return this.tasteVectorId;
	}

	public TasteDimension getDimension() {
		return this.dimension;
	}

	public String getCode() {
		return this.code;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof UserTasteWeightId that)) {
			return false;
		}
		return Objects.equals(this.tasteVectorId, that.tasteVectorId) && this.dimension == that.dimension
				&& Objects.equals(this.code, that.code);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.tasteVectorId, this.dimension, this.code);
	}
}
