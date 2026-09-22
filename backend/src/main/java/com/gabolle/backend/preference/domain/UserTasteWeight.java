package com.gabolle.backend.preference.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
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
 *
 * <p>🔴 <b>한 성분에 줄이 여럿일 수 있다</b> (S15P21E201-1499). 근거가 키의 일부라
 * 설문에서 온 몫과 행동에서 온 몫이 <b>각각 한 줄</b>로 앉는다. 그래서 이 엔티티를 그대로
 * 점수에 쓰면 같은 성분을 두 번 세게 된다 — 읽는 쪽은 반드시
 * {@link TasteWeightComponent#merge(java.util.List)} 를 거친다.
 *
 * <p>{@code BLENDED} 로 적는 길은 더 이상 없다. 예전에 그렇게 적힌 줄은 남아 있고 읽을 때
 * 그대로 한 성분으로 다룬다 — 그 줄의 두 몫은 이미 섞여서 되돌릴 수 없다.
 */
@Entity
@Table(name = "user_taste_weight")
public class UserTasteWeight {

	@EmbeddedId
	private UserTasteWeightId id;

	/** -1(싫다) ~ +1(좋다). DB 가 범위를 막는다. */
	@Column(name = "weight", nullable = false)
	private double weight;

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

	private UserTasteWeight(UserTasteWeightId id, double weight, int support, OffsetDateTime updatedAt) {
		this.id = id;
		this.weight = weight;
		this.support = support;
		this.updatedAt = updatedAt;
	}

	/** 설문에서 사람이 직접 고른 값. 뒷받침 수가 0 이어도 된다 — 관측이 필요 없다. */
	public static UserTasteWeight fromSurvey(UUID tasteVectorId, TasteDimension dimension, String code, double weight,
			OffsetDateTime updatedAt) {
		return new UserTasteWeight(new UserTasteWeightId(tasteVectorId, dimension, code, TasteEvidence.SURVEY), weight,
				0, updatedAt);
	}

	/**
	 * 앱에서의 행동으로 매긴 값. {@code support} 가 0 이면 DB 가 거부한다 — "행동을 봤다" 고
	 * 하면서 아무것도 안 본 것이다.
	 */
	public static UserTasteWeight fromInteraction(UUID tasteVectorId, TasteDimension dimension, String code,
			double weight, int support, OffsetDateTime updatedAt) {
		return new UserTasteWeight(new UserTasteWeightId(tasteVectorId, dimension, code, TasteEvidence.INTERACTION),
				weight, support, updatedAt);
	}

	/**
	 * 🔴 <b>새로 적는 데 쓰지 마라.</b> S15P21E201-1499 이전에 적힌 행을 그대로 나타내기 위해만
	 * 있다 — 그 행들은 설문 몫과 행동 몫이 이미 섞여 되돌릴 수 없고, 읽을 때 한 성분으로
	 * 다뤄야 한다.
	 *
	 * <p>이것이 없으면 그 행을 지나는 길({@link TasteWeightComponent#merge(java.util.List)} 의
	 * {@code BLENDED} 갈래)을 시험이 밟을 수 없다. 그 갈래가 조용히 틀리면 옛 성분이 채점에서
	 * 통째로 빠지므로(채점기가 {@code SURVEY} 를 거른다) 시험이 꼭 필요하다.
	 */
	public static UserTasteWeight legacyBlended(UUID tasteVectorId, TasteDimension dimension, String code,
			double weight, int support, OffsetDateTime updatedAt) {
		return new UserTasteWeight(new UserTasteWeightId(tasteVectorId, dimension, code, TasteEvidence.BLENDED), weight,
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
		return this.id.getEvidence();
	}

	public int getSupport() {
		return this.support;
	}

	public OffsetDateTime getUpdatedAt() {
		return this.updatedAt;
	}
}
