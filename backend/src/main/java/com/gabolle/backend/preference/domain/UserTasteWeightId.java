package com.gabolle.backend.preference.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/**
 * {@link UserTasteWeight} 의 복합 키 — (판, 차원, 코드, 근거).
 *
 * <p>🔴 <b>근거가 키에 있는 이유</b> (S15P21E201-1499). 예전에는 (판, 차원, 코드) 셋뿐이라
 * 한 성분에 줄이 하나였고, 설문 몫과 행동 몫이 같은 성분을 가리키면 {@code BLENDED} 한 줄로
 * 합쳐졌다. 그러면 <b>그 줄에서 행동 몫이 얼마였는지가 사라진다.</b> 배치는 접을 때마다 전
 * 이력을 다시 계산하므로 상관없었지만, 이벤트 하나만 들고 오는 소비자는 그 상태에서
 * 「얼마를 더할지」를 계산할 수 없다.
 *
 * <p>그래서 <b>나눠서 적고, 합치는 것은 읽는 쪽에서</b> 한다
 * ({@link TasteWeightComponent#merge(java.util.List)}).
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
	 * 차원 안의 값. 예: {@code dimension=CATEGORY} 일 때 {@code code=CAFE}. 코드 목록은 화면
	 * 옵션과 장소 태그 온톨로지가 확정된 뒤에 고정한다 — 그전까지는 DB 도 막지 않는다.
	 */
	@Column(name = "code", nullable = false, length = 50, updatable = false)
	private String code;

	/** 이 숫자가 어디서 나왔는가. 같은 성분이라도 출처가 다르면 다른 줄이다. */
	@Enumerated(EnumType.STRING)
	@Column(name = "evidence", nullable = false, length = 20, updatable = false)
	private TasteEvidence evidence;

	protected UserTasteWeightId() {
	}

	public UserTasteWeightId(UUID tasteVectorId, TasteDimension dimension, String code, TasteEvidence evidence) {
		this.tasteVectorId = tasteVectorId;
		this.dimension = dimension;
		this.code = code;
		this.evidence = evidence;
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

	public TasteEvidence getEvidence() {
		return this.evidence;
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
				&& Objects.equals(this.code, that.code) && this.evidence == that.evidence;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.tasteVectorId, this.dimension, this.code, this.evidence);
	}
}
