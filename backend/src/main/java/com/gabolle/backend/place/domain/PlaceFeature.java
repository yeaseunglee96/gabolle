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
 * 장소에 대한 사실 하나. 넓은 표 한 행이 아니라 <b>사실 하나 = 한 행</b> 이다 (S15P21E201-545).
 *
 * <p>그렇게 나눈 이유는 피처마다 출처와 확인 상태를 따로 가져야 하기 때문이다. 칼럼으로 늘어놓으면
 * "카테고리는 검증됐고 알레르기는 추정" 을 적을 칸이 없다.
 *
 * <h2>🔴 {@link #featureType} 과 {@link #featureKey} 가 String 인 이유</h2>
 *
 * S15P21E201-473 의 완료 기준이 <b>"장소에 표식을 새로 붙이면 코드를 고치지 않아도 그 장소가
 * 나온다"</b> 이다. 자바 enum 에 갈래를 박으면 이 기준은 구조적으로 못 지킨다 — 새 값이 들어오는
 * 순간 {@code IllegalArgumentException} 이 나거나 조용히 빠진다.
 *
 * <p>값 목록의 정본은 DB 다. {@code ck_place_feature_type} 이 14종을, {@code ck_place_feature_key_shape}
 * 가 "태그형은 키가 있고 그 밖은 없다" 를 강제한다. 안쪽 코드값(어떤 관심 태그인지)에는 CHECK 가
 * 아직 없는데, 그것은 온톨로지가 확정되지 않아서 <b>일부러</b> 비워 둔 자리다 (V20260904020000 86행).
 * 여기서 enum 을 만들면 우리가 그 결정을 대신 내리는 셈이 된다.
 *
 * <p>반대로 {@link MatchKind} 는 enum 이다. 그쪽은 값이 늘면 비교 알고리즘이 달라져 어차피 코드가
 * 필요하다. 데이터로 늘어나는 것은 String, 동작이 바뀌는 것은 enum.
 *
 * <h2>🔴 {@link #placeId} 가 {@code @ManyToOne} 이 아닌 이유</h2>
 *
 * S15P21E201-102 의 완료 기준이 "질의 개수가 장소 수에 비례하지 않는다" 다. 연관을 걸면 후보를
 * 순회하면서 장소마다 질의가 나가는 길이 열리고, 그것은 테스트로 잡기 전에는 안 보인다. 아이디만
 * 들고 있으면 {@code WHERE placeId IN :ids} 한 번으로 묶을 수밖에 없다.
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

	/** 🔴 {@link PlaceEvidenceStatus#UNKNOWN} 이면 반드시 {@code null} 이다 (DB CHECK 가 강제). */
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
	 * 이 행이 <b>"그 표식이 있다"</b> 는 근거가 되는가.
	 *
	 * <p>두 가지를 다 걸러야 한다.
	 *
	 * <ul>
	 * <li>{@link PlaceEvidenceStatus#UNKNOWN} 은 "모른다" 이지 "있다" 가 아니다. 뭉개면 확인 안 된
	 *     장소가 그 갈래에 섞인다.</li>
	 * <li>🔴 확인했는데 결과가 "아니다" 인 경우도 있다 — {@code VERIFIED} 에 값이 {@code false} 인
	 *     행이다. 휠체어 접근이 <b>안 되는 것으로 확인된</b> 장소가 여기다. 상태만 보고 값을 안 보면
	 *     그 장소가 "휠체어 접근 가능" 목록에 들어간다.</li>
	 * </ul>
	 *
	 * <p>🔴 값의 모양은 아직 데이터 담당이 확정하지 않았다. 그래서 <b>JSON 리터럴 {@code false}
	 * 하나만</b> "확인된 해당 없음" 으로 읽는다. {@code {"present": false}} 같은 다른 표현은 인식하지
	 * 않는다 — 인식하는 척하면 그 모양이 계약인 것처럼 굳는다. 값 모양이 정해지면 이 메서드 하나만
	 * 고치면 된다.
	 */
	public boolean indicatesPresence() {
		// S15P21E201-604 — 판정식은 FeaturePresence 로 옮겼다. 추천 엔진이 PlaceFeatureView
		// (엔티티가 아닌 조회 응답)에도 같은 판정을 해야 해서, 엔티티 메서드 안에 갇혀 있으면
		// 그 경로에서 판정을 다시 만들어야 하고 조용히 갈라질 여지가 생긴다.
		return FeaturePresence.indicatesPresence(
				this.evidenceStatus == null ? null : this.evidenceStatus.name(), this.value);
	}

	/**
	 * 없다고 <b>단정할 수 없는가</b>. 안전 제약을 거를 때 쓴다.
	 *
	 * <p>🔴 {@link #indicatesPresence()} 의 반대가 아니다. 알레르기처럼 위반이면 빼야 하는 조건에서는
	 * "모른다" 를 통과시키면 안 된다 — 땅콩이 들었는지 확인 안 된 식당을 안전한 것처럼 내보내게 된다.
	 * 마이그레이션 {@code V20260904020000} 의 대조표 주석이 같은 말을 한다: "정보가 없으면 PASS 로
	 * 바꾸지 않고 UNKNOWN 으로 둔다".
	 *
	 * <p>그래서 <b>확인된 부재만</b> 통과시킨다. 모르는 것은 있는 것으로 취급한다.
	 */
	public boolean cannotRuleOutPresence() {
		return FeaturePresence.cannotRuleOutPresence(
				this.evidenceStatus == null ? null : this.evidenceStatus.name(), this.value);
	}
}
