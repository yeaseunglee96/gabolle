package com.gabolle.backend.preference.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/**
 * {@link UserTasteWeight} 의 복합 키 — (판, 차원, 코드).
 *
 * <p>{@code place} 패키지의 {@code UserPlaceCodeMapId} 와 같은 모양으로 맞췄다
 * ({@code @EmbeddedId}). 이 저장소에 이미 있는 방식을 따른다.
 */
@Embeddable
public class UserTasteWeightId implements Serializable {

	private static final long serialVersionUID = 1L;

	@Column(name = "taste_vector_id", nullable = false, updatable = false)
	private UUID tasteVectorId;

	@Enumerated(EnumType.STRING)
	@Column(name = "dimension", nullable = false, length = 50, updatable = false)
	private TasteDimension dimension;

	/**
	 * 차원 안의 값. 예: {@code dimension=CATEGORY} 일 때 {@code code=CAFE}.
	 *
	 * <p>🔴 코드 목록은 화면 옵션과 장소 태그 온톨로지가 확정된 뒤 고정한다. 그전까지
	 * DB 도 막지 않는다 — {@code preference_answer} 가 {@code value} 안쪽을 안 막은 것과
	 * 같은 이유다. 지어낸 목록이 계약이 되는 것보다 담당자가 정하고 나서 박는 것이 맞다.
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
