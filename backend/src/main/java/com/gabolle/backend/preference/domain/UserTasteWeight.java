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
	 * 눌러 담기 <b>전</b> 의 기여값 합 (S15P21E201-1500). {@code weight} 는 이 값에서 만든다.
	 *
	 * <p>🔴 <b>소비자가 증분으로 더하는 자리다.</b> 눌러 담은 {@code weight} 에 기여값을 더하는
	 * 것은 뜻이 없다 — 0.4 에 1.0 을 더하면 1.4 이지 「하트가 하나 늘었다」가 아니다. 더하기는
	 * 언제나 이 칸에 하고, {@code weight} 는 그 뒤에 다시 만든다.
	 *
	 * <p>설문 성분은 0 이다. 설문 무게는 사람이 고른 값이지 관측의 합이 아니라 이 개념이 없다.
	 */
	@Column(name = "raw", nullable = false)
	private double raw;

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

	/**
	 * 「몇 건이면 확신하나」. {@link #confidence(double)} 의 {@code K} 다 — 같은 태그로 K 건이
	 * 모이면 0.5, 3K 건이면 0.75 가 된다. 올리면 더 신중해지고 내리면 성급해진다.
	 *
	 * <p>🔴 이 값이 도메인에 있는 이유 (S15P21E201-1500). 예전에는 접는 배치 안에만 있었는데,
	 * 이제 카프카 소비자도 같은 변환을 해야 한다. 두 곳에 두면 반드시 어긋나고, 어긋나면
	 * 「배치가 만든 값과 소비자가 만든 값이 다르다」가 오류 없이 생긴다.
	 */
	public static final double CONFIDENCE_K = 3.0;

	private UserTasteWeight(UserTasteWeightId id, double raw, double weight, int support, OffsetDateTime updatedAt) {
		this.id = id;
		this.raw = raw;
		this.weight = weight;
		this.support = support;
		this.updatedAt = updatedAt;
	}

	/**
	 * 쌓인 힘을 {@code -1 ~ +1} 무게로 옮긴다. 건수가 늘수록 1 에 가까워지되 <b>절대 넘지
	 * 않는다</b> — {@code ck_user_taste_weight_range} 가 범위를 막기도 하지만, 잘려서
	 * 통과하는 것과 애초에 그 안에 있는 것은 다르다. 잘리면 100 건과 1000 건이 같은 값이 된다.
	 */
	public static double confidence(double raw) {
		return raw / (Math.abs(raw) + CONFIDENCE_K);
	}

	/** 설문에서 사람이 직접 고른 값. 뒷받침 수가 0 이어도 된다 — 관측이 필요 없다. */
	public static UserTasteWeight fromSurvey(UUID tasteVectorId, TasteDimension dimension, String code, double weight,
			OffsetDateTime updatedAt) {
		return new UserTasteWeight(new UserTasteWeightId(tasteVectorId, dimension, code, TasteEvidence.SURVEY), 0.0,
				weight, 0, updatedAt);
	}

	/**
	 * 앱에서의 행동으로 매긴 값. {@code support} 가 0 이면 DB 가 거부한다 — "행동을 봤다" 고
	 * 하면서 아무것도 안 본 것이다.
	 *
	 * <p>🔴 <b>{@code weight} 를 받지 않고 여기서 만든다</b> (S15P21E201-1500). 둘을 따로 받으면
	 * 서로 안 맞는 짝이 저장될 수 있고, 그러면 다음에 증분으로 더한 값이 조용히 틀린다.
	 */
	public static UserTasteWeight fromInteraction(UUID tasteVectorId, TasteDimension dimension, String code,
			double raw, int support, OffsetDateTime updatedAt) {
		return new UserTasteWeight(new UserTasteWeightId(tasteVectorId, dimension, code, TasteEvidence.INTERACTION),
				raw, confidence(raw), support, updatedAt);
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
		// raw 는 0 이다. 이 행의 weight 는 clamp(설문 + 행동) 이라 행동 몫만 떼어낼 수 없고,
		// 되돌리면 설문까지 행동인 것처럼 부풀려진다. 마이그레이션이 이 행들을 건너뛰는 것과
		// 같은 이유다.
		return new UserTasteWeight(new UserTasteWeightId(tasteVectorId, dimension, code, TasteEvidence.BLENDED), 0.0,
				weight, support, updatedAt);
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

	public double getRaw() {
		return this.raw;
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
