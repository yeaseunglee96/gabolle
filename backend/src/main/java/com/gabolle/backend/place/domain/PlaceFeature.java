package com.gabolle.backend.place.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 장소에 대한 사실 하나 = 한 행. 칼럼으로 늘어놓으면 피처마다 출처와 확인 상태를 따로 적을 칸이 없다.
 *
 * <p>{@link #featureType} 과 {@link #featureKey} 가 String 인 이유: 장소에 표식을 새로 붙여도 코드를
 * 고치지 않고 나와야 한다. 값 목록의 정본은 DB 이고 ({@code ck_place_feature_type} 이 14종,
 * {@code ck_place_feature_key_shape} 가 "태그형은 키가 있고 그 밖은 없다"), 안쪽 코드값에 CHECK 가
 * 없는 것은 온톨로지가 확정되지 않아 일부러 비워 둔 자리다. 데이터로 늘어나는 것은 String,
 * 동작이 바뀌는 것은 enum ({@link MatchKind}).
 *
 * <p>{@link #placeId} 가 {@code @ManyToOne} 이 아닌 이유: 연관을 걸면 후보를 순회하며 장소마다 질의가
 * 나가는 길이 열린다. 아이디만 들고 있으면 {@code WHERE placeId IN :ids} 한 번으로 묶을 수밖에 없다.
 */
@Entity
@Table(name = "place_feature")
public class PlaceFeature {

	@Id
	@Column(name = "place_feature_id", nullable = false, updatable = false)
	private UUID placeFeatureId;

	@Column(name = "place_id", nullable = false, updatable = false)
	private UUID placeId;

	/** 피처 14종. 값 목록은 {@code ck_place_feature_type} 이 정본이다. 자바에서 검증하지 않는다. */
	@Column(name = "feature_type", nullable = false, length = 50)
	private String featureType;

	/** 태그형이면 코드가 있고 점수형·참거짓형이면 {@code null} 이다 ({@code ck_place_feature_key_shape}). */
	@Column(name = "feature_key", length = 50)
	private String featureKey;

	/** {@link PlaceEvidenceStatus#UNKNOWN} 이면 반드시 {@code null} 이다 (DB CHECK 가 강제). */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "value")
	private String value;

	@Enumerated(EnumType.STRING)
	@Column(name = "evidence_status", nullable = false, length = 20)
	private PlaceEvidenceStatus evidenceStatus;

	@Column(name = "source_type", length = 50)
	private String sourceType;

	@Column(name = "source_id", length = 200)
	private String sourceId;

	/** 원천에서 이 사실이 관측된 시각. 우리가 가져온 시각과 다르다. */
	@Column(name = "observed_at")
	private OffsetDateTime observedAt;

	@Column(name = "source_version", length = 100)
	private String sourceVersion;

	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	protected PlaceFeature() {
	}

	/**
	 * 외부 자료에서 가져온 사실 하나를 만든다.
	 *
	 * <p>{@link PlaceEvidenceStatus#UNKNOWN} 으로는 만들지 않는다 — "모른다" 는 행을 안 넣는 것이다
	 * ({@code ck_place_feature_unknown_has_no_value}). 여기서 막지 않으면 "모른다" 가 조용히
	 * "있다" 로 세어진다. 안전 항목(알레르기·식단·접근성)은 {@code ESTIMATED} 로 넣을 수 없다
	 * ({@code ck_place_feature_safety_never_estimated}).
	 *
	 * @param value JSON 문자열. 태그형은 보통 {@code "true"}, 점수형은 {@code "0.7"} 이나
	 *     {@code "{\"score\":0.7}"}
	 */
	public static PlaceFeature imported(UUID placeFeatureId, UUID placeId, String featureType,
			String featureKey, String value, PlaceEvidenceStatus evidenceStatus,
			String sourceType, String sourceId, OffsetDateTime observedAt, String sourceVersion,
			OffsetDateTime createdAt) {
		if (evidenceStatus == null || evidenceStatus == PlaceEvidenceStatus.UNKNOWN) {
			throw new IllegalArgumentException(
					"UNKNOWN 피처는 넣지 않는다 — 모른다는 것은 행이 없다는 뜻이다: " + featureType);
		}
		PlaceFeature feature = new PlaceFeature();
		feature.placeFeatureId = placeFeatureId;
		feature.placeId = placeId;
		feature.featureType = featureType;
		feature.featureKey = featureKey;
		feature.value = value;
		feature.evidenceStatus = evidenceStatus;
		feature.sourceType = sourceType;
		feature.sourceId = sourceId;
		feature.observedAt = observedAt;
		feature.sourceVersion = sourceVersion;
		feature.createdAt = createdAt;
		return feature;
	}

	public UUID getPlaceFeatureId() {
		return placeFeatureId;
	}

	public UUID getPlaceId() {
		return placeId;
	}

	public String getFeatureType() {
		return featureType;
	}

	public String getFeatureKey() {
		return featureKey;
	}

	public String getValue() {
		return value;
	}

	public PlaceEvidenceStatus getEvidenceStatus() {
		return evidenceStatus;
	}

	public String getSourceType() {
		return sourceType;
	}

	public String getSourceId() {
		return sourceId;
	}

	public OffsetDateTime getObservedAt() {
		return observedAt;
	}

	public String getSourceVersion() {
		return sourceVersion;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}

	/**
	 * 이 행이 "그 표식이 있다" 는 근거가 되는가. 상태와 값을 둘 다 본다 —
	 * {@link PlaceEvidenceStatus#UNKNOWN} 은 "있다" 가 아니고, {@code VERIFIED} 에 값이
	 * {@code false} 인 행은 "확인했더니 아니다" 라서 상태만 보면 반대 목록에 섞인다.
	 * 값의 모양이 미확정이라 JSON 리터럴 {@code false} 하나만 "확인된 해당 없음" 으로 읽는다.
	 */
	public boolean indicatesPresence() {
		// 판정식은 FeaturePresence 에 있다. 추천 엔진이 엔티티가 아닌 조회 응답에도 같은 판정을
		// 해야 해서, 엔티티 메서드 안에 갇혀 있으면 두 경로가 조용히 갈라진다.
		return FeaturePresence.indicatesPresence(
				this.evidenceStatus == null ? null : this.evidenceStatus.name(), this.value);
	}

	/**
	 * 없다고 단정할 수 없는가. 안전 제약을 거를 때 쓴다. {@link #indicatesPresence()} 의 반대가
	 * 아니다 — 확인된 부재만 통과시키고 모르는 것은 있는 것으로 취급한다. 땅콩이 들었는지 확인
	 * 안 된 식당을 안전한 것처럼 내보내면 안 되기 때문이다.
	 */
	public boolean cannotRuleOutPresence() {
		return FeaturePresence.cannotRuleOutPresence(
				this.evidenceStatus == null ? null : this.evidenceStatus.name(), this.value);
	}
}
