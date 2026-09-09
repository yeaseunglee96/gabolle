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
 * <h2>🔴 안 물어본 차원은 행을 만들지 않는다. 0 을 넣지 않는다.</h2>
 *
 * 이 저장소는 같은 원칙을 이미 DB 로 강제하고 있다 —
 * {@code preference_answer} 는 건너뜀·모름에 값을 실을 수 없다
 * ({@code ck_preference_answer_value_matches_status}).
 *
 * <p>그 원칙이 <b>벡터에서 가장 깨지기 쉽다.</b> 벡터는 빈 칸을 0 으로 채우는 것이
 * 수학적으로 자연스러워 보이기 때문이다. 실제로 대부분의 추천 코드가 그렇게 한다.
 *
 * <p>그런데 0 은 <b>"싫지도 좋지도 않다" 는 의견</b>이고, 행이 없는 것은
 * <b>"안 물어봤다" 는 무지</b>다. 한 번 0 으로 적으면 둘을 영영 구분할 수 없고,
 * 그때부터 추천은 "이 사람은 카페에 관심 없다" 를 근거로 카페를 빼기 시작한다 —
 * 물어본 적도 없이. 그리고 그 사람은 카페 추천을 못 받는 이유를 영영 모른다.
 *
 * <p>계산할 때 0 이 필요하면 <b>읽는 쪽에서 그 순간 0 으로 다룬다.</b> 저장은 안 한다.
 */
@Entity
@Table(name = "user_taste_weight")
public class UserTasteWeight {

	@EmbeddedId
	private UserTasteWeightId id;

	/** -1(싫다) ~ +1(좋다). DB 가 범위를 막는다({@code ck_user_taste_weight_range}). */
	@Column(name = "weight", nullable = false)
	private double weight;

	@Enumerated(EnumType.STRING)
	@Column(name = "evidence", nullable = false, length = 20)
	private TasteEvidence evidence;

	/**
	 * 이 값을 뒷받침한 관측 수.
	 *
	 * <p>🔴 1건으로 매긴 0.9 와 200건으로 매긴 0.9 는 <b>다른 값</b>이다. 이걸 안 남기면
	 * 둘이 같아 보이고, 추천은 우연히 한 번 누른 것을 확신처럼 다루게 된다.
	 * {@link TasteEvidence#SURVEY} 가 아닌 값은 이것이 0 일 수 없다 — DB 가 막는다.
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

	/**
	 * 설문에서 사람이 직접 고른 값.
	 *
	 * <p>뒷받침 수가 0 이어도 된다 — 사람이 직접 말한 것에는 관측이 필요 없다.
	 */
	public static UserTasteWeight fromSurvey(UUID tasteVectorId, TasteDimension dimension, String code, double weight,
			OffsetDateTime updatedAt) {
		return new UserTasteWeight(new UserTasteWeightId(tasteVectorId, dimension, code), weight, TasteEvidence.SURVEY,
				0, updatedAt);
	}

	/**
	 * 앱에서의 행동으로 매긴 값.
	 *
	 * <p>🔴 {@code support} 가 0 이면 DB 가 거부한다 — "행동을 봤다" 고 주장하면서 아무것도
	 * 안 본 것이기 때문이다. 그런 값은 만들 수 없어야 한다.
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
