package com.gabolle.backend.recommendation.domain;

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
 * 추천 요청 한 건에서 생성된 후보 하나. 분석 단위는 {@code (request_id, place_id)} 다.
 *
 * Top-K 에 못 든 후보도 지우지 않는다 — 탈락 이유는 그 순간에만 남길 수 있다. JSONB 컬럼은
 * 문자열로 들고 다니되 저장 시 jsonb 로 넘어간다.
 */
@Entity
@Table(name = "recommendation_candidate")
public class RecommendationCandidate {

	@Id
	@Column(name = "candidate_id", nullable = false, updatable = false)
	private UUID candidateId;

	@Column(name = "request_id", nullable = false, updatable = false)
	private UUID requestId;

	@Column(name = "place_id", nullable = false, updatable = false)
	private UUID placeId;

	@Column(name = "candidate_source", nullable = false, length = 50)
	private String candidateSource;

	@Enumerated(EnumType.STRING)
	@Column(name = "candidate_stage", nullable = false, length = 30)
	private CandidateStage candidateStage;

	@Column(name = "eligible", nullable = false)
	private boolean eligible;

	@Enumerated(EnumType.STRING)
	@Column(name = "constraint_verdict", nullable = false, length = 10)
	private ConstraintVerdict constraintVerdict;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "violations", nullable = false)
	private String violations;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "unknown_facts", nullable = false)
	private String unknownFacts;

	@Column(name = "constraint_confidence")
	private Double constraintConfidence;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "feature_values", nullable = false)
	private String featureValues;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "score_components", nullable = false)
	private String scoreComponents;

	@Column(name = "pre_rank_score")
	private Double preRankScore;

	@Column(name = "final_score")
	private Double finalScore;

	@Column(name = "original_rank")
	private Integer originalRank;

	@Column(name = "final_rank")
	private Integer finalRank;

	@Column(name = "returned", nullable = false)
	private boolean returned;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "reason_codes", nullable = false)
	private String[] reasonCodes;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "warning_codes", nullable = false)
	private String[] warningCodes;

	@Enumerated(EnumType.STRING)
	@Column(name = "fallback_mode", length = 20)
	private FallbackMode fallbackMode;

	/**
	 * 이 후보가 개인화 추천인가 Editor's Pick 인가({@link SourceMode}). 요청 행에도 있는
	 * 값을 후보 행에 또 두는 것은 후보만 보고도 집계할 수 있게 하기 위해서다 — 매번
	 * recommendation_job 과 조인해야 하면 분석 질의마다 그 조인을 잊을 기회가 생긴다.
	 */
	@Enumerated(EnumType.STRING)
	@Column(name = "source_mode", nullable = false, length = 20)
	private SourceMode sourceMode;

	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	protected RecommendationCandidate() {
		// JPA 전용
	}

	private RecommendationCandidate(Builder builder) {
		this.candidateId = builder.candidateId;
		this.requestId = builder.requestId;
		this.placeId = builder.placeId;
		this.candidateSource = builder.candidateSource;
		this.candidateStage = builder.candidateStage;
		this.eligible = builder.eligible;
		this.constraintVerdict = builder.constraintVerdict;
		this.violations = builder.violations;
		this.unknownFacts = builder.unknownFacts;
		this.constraintConfidence = builder.constraintConfidence;
		this.featureValues = builder.featureValues;
		this.scoreComponents = builder.scoreComponents;
		this.preRankScore = builder.preRankScore;
		this.finalScore = builder.finalScore;
		this.originalRank = builder.originalRank;
		this.finalRank = builder.finalRank;
		this.returned = builder.returned;
		this.reasonCodes = builder.reasonCodes;
		this.warningCodes = builder.warningCodes;
		this.fallbackMode = builder.fallbackMode;
		this.sourceMode = builder.sourceMode;
		this.createdAt = builder.createdAt;
		validateInvariants();
	}

	/**
	 * DB CHECK 와 같은 것을 애플리케이션에서도 본다. DB 제약 위반은 스택이 JDBC 안쪽에서
	 * 끊겨 원인 코드를 못 가리키기 때문이다.
	 */
	private void validateInvariants() {
		if (constraintVerdict == ConstraintVerdict.FAIL && returned) {
			throw new IllegalStateException(
					"하드 제약을 위반한 후보(FAIL)는 노출될 수 없다: place_id=" + placeId);
		}
		if (constraintVerdict == ConstraintVerdict.FAIL && eligible) {
			throw new IllegalStateException(
					"하드 제약을 위반한 후보(FAIL)는 랭킹 대상이 될 수 없다: place_id=" + placeId);
		}
		if (returned && (finalRank == null || finalScore == null)) {
			throw new IllegalStateException(
					"노출된 후보는 최종 순위와 점수가 있어야 한다: place_id=" + placeId);
		}
		if (returned && candidateStage != CandidateStage.RETURNED) {
			throw new IllegalStateException(
					"노출된 후보의 단계는 RETURNED 여야 한다: place_id=" + placeId);
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	public UUID getCandidateId() {
		return candidateId;
	}

	public UUID getRequestId() {
		return requestId;
	}

	public UUID getPlaceId() {
		return placeId;
	}

	public String getCandidateSource() {
		return candidateSource;
	}

	public CandidateStage getCandidateStage() {
		return candidateStage;
	}

	public boolean isEligible() {
		return eligible;
	}

	public ConstraintVerdict getConstraintVerdict() {
		return constraintVerdict;
	}

	public String getViolations() {
		return violations;
	}

	public String getUnknownFacts() {
		return unknownFacts;
	}

	public Double getConstraintConfidence() {
		return constraintConfidence;
	}

	public String getFeatureValues() {
		return featureValues;
	}

	public String getScoreComponents() {
		return scoreComponents;
	}

	public Double getPreRankScore() {
		return preRankScore;
	}

	public Double getFinalScore() {
		return finalScore;
	}

	public Integer getOriginalRank() {
		return originalRank;
	}

	public Integer getFinalRank() {
		return finalRank;
	}

	public boolean isReturned() {
		return returned;
	}

	public String[] getReasonCodes() {
		return reasonCodes == null ? new String[0] : reasonCodes.clone();
	}

	public String[] getWarningCodes() {
		return warningCodes == null ? new String[0] : warningCodes.clone();
	}

	public FallbackMode getFallbackMode() {
		return fallbackMode;
	}

	public SourceMode getSourceMode() {
		return sourceMode;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}

	/** 필드가 스물넷이라 생성자 인자 순서로는 아무도 못 읽는다. */
	public static final class Builder {

		private UUID candidateId;
		private UUID requestId;
		private UUID placeId;
		private String candidateSource;
		private CandidateStage candidateStage;
		private boolean eligible;
		private ConstraintVerdict constraintVerdict;
		private String violations = "[]";
		private String unknownFacts = "[]";
		private Double constraintConfidence;
		private String featureValues = "{}";
		private String scoreComponents = "{}";
		private Double preRankScore;
		private Double finalScore;
		private Integer originalRank;
		private Integer finalRank;
		private boolean returned;
		private String[] reasonCodes = new String[0];
		private String[] warningCodes = new String[0];
		private FallbackMode fallbackMode;

		/** 기본값이 {@link SourceMode#PERSONALIZED} 이고, Pick 경로만 명시적으로 바꾼다. */
		private SourceMode sourceMode = SourceMode.PERSONALIZED;

		private OffsetDateTime createdAt;

		private Builder() {
		}

		public Builder candidateId(UUID value) {
			this.candidateId = value;
			return this;
		}

		public Builder requestId(UUID value) {
			this.requestId = value;
			return this;
		}

		public Builder placeId(UUID value) {
			this.placeId = value;
			return this;
		}

		public Builder candidateSource(String value) {
			this.candidateSource = value;
			return this;
		}

		public Builder candidateStage(CandidateStage value) {
			this.candidateStage = value;
			return this;
		}

		public Builder eligible(boolean value) {
			this.eligible = value;
			return this;
		}

		public Builder constraintVerdict(ConstraintVerdict value) {
			this.constraintVerdict = value;
			return this;
		}

		public Builder violations(String value) {
			this.violations = (value == null) ? "[]" : value;
			return this;
		}

		public Builder unknownFacts(String value) {
			this.unknownFacts = (value == null) ? "[]" : value;
			return this;
		}

		public Builder constraintConfidence(Double value) {
			this.constraintConfidence = value;
			return this;
		}

		public Builder featureValues(String value) {
			this.featureValues = (value == null) ? "{}" : value;
			return this;
		}

		public Builder scoreComponents(String value) {
			this.scoreComponents = (value == null) ? "{}" : value;
			return this;
		}

		public Builder preRankScore(Double value) {
			this.preRankScore = value;
			return this;
		}

		public Builder finalScore(Double value) {
			this.finalScore = value;
			return this;
		}

		public Builder originalRank(Integer value) {
			this.originalRank = value;
			return this;
		}

		public Builder finalRank(Integer value) {
			this.finalRank = value;
			return this;
		}

		public Builder returned(boolean value) {
			this.returned = value;
			return this;
		}

		public Builder reasonCodes(String[] value) {
			this.reasonCodes = (value == null) ? new String[0] : value.clone();
			return this;
		}

		public Builder warningCodes(String[] value) {
			this.warningCodes = (value == null) ? new String[0] : value.clone();
			return this;
		}

		public Builder fallbackMode(FallbackMode value) {
			this.fallbackMode = value;
			return this;
		}

		public Builder sourceMode(SourceMode value) {
			this.sourceMode = value;
			return this;
		}

		public Builder createdAt(OffsetDateTime value) {
			this.createdAt = value;
			return this;
		}

		public RecommendationCandidate build() {
			return new RecommendationCandidate(this);
		}
	}
}
