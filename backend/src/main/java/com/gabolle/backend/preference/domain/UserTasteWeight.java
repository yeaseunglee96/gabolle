package com.gabolle.backend.preference.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * 취향 벡터의 성분 하나 — "이 사람은 이 차원의 이 값을 얼마나 좋아하는가".
 *
 * <p>안 물어본 차원은 행을 만들지 않는다. 0 을 넣지 않는다. 빈 칸을 0 으로 채우는 것이
 * 수학적으로 자연스러워 보이지만, 0 은 "싫지도 좋지도 않다" 는 의견이고 행이 없는 것은
 * "안 물어봤다" 는 무지다. 한 번 0 으로 적으면 둘을 영영 구분할 수 없고, 그때부터 추천은
 * 물어본 적도 없이 그것을 근거로 후보를 뺀다.
 *
 * <p>계산할 때 0 이 필요하면 읽는 쪽에서 그 순간 0 으로 다룬다. 저장은 안 한다.
 */
@Entity
@Table(name = "user_taste_weight")
public class UserTasteWeight {

	@EmbeddedId
	private UserTasteWeightId id;

	/** -1(싫다) ~ +1(좋다). DB 가 범위를 막는다. */
	@Column(name = "weight", nullable = false)
	private double weight;

	@Enumerated(EnumType.STRING)
	@Column(name = "evidence", nullable = false, length = 20)
	private TasteEvidence evidence;

	/**
	 * 이 값을 뒷받침한 관측 수. 1건으로 매긴 0.9 와 200건으로 매긴 0.9 는 다른 값이고, 안
	 * 남기면 추천이 우연히 한 번 누른 것을 확신처럼 다룬다.
	 * {@link TasteEvidence#SURVEY} 가 아닌 값은 0 일 수 없다 — DB 가 막는다.
	 */
	@Column(name = "support", nullable = false)
	private int support;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected UserTasteWeight() {
	}

	private UserTasteWeight(UserTasteWeightId id, double weight, TasteEvidence evidence, int support,
			OffsetDateTime updatedAt) {
		this.id = id;
		this.weight = weight;
		this.evidence = evidence;
		this.support = support;
		this.updatedAt = updatedAt;
	}

	/** 설문에서 사람이 직접 고른 값. 뒷받침 수가 0 이어도 된다 — 관측이 필요 없다. */
	public static UserTasteWeight fromSurvey(UUID tasteVectorId, TasteDimension dimension, String code, double weight,
			OffsetDateTime updatedAt) {
		return new UserTasteWeight(new UserTasteWeightId(tasteVectorId, dimension, code), weight, TasteEvidence.SURVEY,
				0, updatedAt);
	}

	/**
	 * 앱에서의 행동으로 매긴 값. {@code support} 가 0 이면 DB 가 거부한다 — "행동을 봤다" 고
	 * 하면서 아무것도 안 본 것이다.
	 */
	public static UserTasteWeight fromInteraction(UUID tasteVectorId, TasteDimension dimension, String code,
			double weight, int support, OffsetDateTime updatedAt) {
		return new UserTasteWeight(new UserTasteWeightId(tasteVectorId, dimension, code), weight,
				TasteEvidence.INTERACTION, support, updatedAt);
	}

	/** 설문 답을 행동으로 보정한 값. */
	public static UserTasteWeight blended(UUID tasteVectorId, TasteDimension dimension, String code, double weight,
			int support, OffsetDateTime updatedAt) {
		return new UserTasteWeight(new UserTasteWeightId(tasteVectorId, dimension, code), weight, TasteEvidence.BLENDED,
				support, updatedAt);
	}

	public UserTasteWeightId getId() {
		return this.id;
	}

	public UUID getTasteVectorId() {
		return this.id.getTasteVectorId();
	}

	public TasteDimension getDimension() {
		return this.id.getDimension();
	}

	public String getCode() {
		return this.id.getCode();
	}

	public double getWeight() {
		return this.weight;
	}

	public TasteEvidence getEvidence() {
		return this.evidence;
	}

	public int getSupport() {
		return this.support;
	}

	public OffsetDateTime getUpdatedAt() {
		return this.updatedAt;
	}
}
