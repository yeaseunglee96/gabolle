package com.gabolle.backend.itinerary.infra;

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

import com.gabolle.backend.itinerary.domain.ItineraryVersion;

/**
 * {@code itinerary_versions} 표 매핑.
 * {@code request_id} 는 {@link UUID} 가 아니라 {@link String} 이다 — 사용자 편집은 진짜 추천
 * 요청에서 나온 것이 아니라 컨트롤러가 편집마다 {@code "req_edit_<uuid>"} 형태로 새로 만든다.
 * {@code uq_itinerary_version UNIQUE (itinerary_id, version)} 이 이 클래스가 지키려는 전부다 —
 * {@link JpaItineraryRepository#append} 가 그 위반을 {@code StaleItineraryVersionException} 으로
 * 바꾼다.
 */
@Entity
@Table(name = "itinerary_versions")
public class ItineraryVersionJpaEntity {

	@Id
	@Column(name = "itinerary_version_id")
	private UUID itineraryVersionId;

	@Column(name = "itinerary_id", nullable = false, updatable = false)
	private UUID itineraryId;

	@Column(name = "version", nullable = false, updatable = false)
	private int version;

	@Column(name = "base_version", updatable = false)
	private Integer baseVersion;

	@Enumerated(EnumType.STRING)
	@Column(name = "operation", nullable = false, length = 20, updatable = false)
	private ItineraryVersion.Operation operation;

	@Column(name = "created_by", nullable = false, updatable = false)
	private UUID createdBy;

	@Column(name = "request_id", nullable = false, length = 64, updatable = false)
	private String requestId;

	/**
	 * 추천이 진짜 판을 만들었을 때만 채워진다. {@code requestId}(VARCHAR)와 다른 칸이다.
	 */
	@Column(name = "source_request_id", updatable = false)
	private UUID sourceRequestId;

	@Column(name = "model_version", length = 100, updatable = false)
	private String modelVersion;

	@Column(name = "feature_version", length = 100, updatable = false)
	private String featureVersion;

	@Column(name = "ontology_version", length = 100, updatable = false)
	private String ontologyVersion;

	@Column(name = "policy_version", length = 100, updatable = false)
	private String policyVersion;

	@Column(name = "dataset_version", length = 100, updatable = false)
	private String datasetVersion;

	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	/**
	 * 판 전체에 대한 경고 코드. 항목이 아예 없는 시간대에 대한 경고는 항목 행에 적을 자리가 없어
	 * 판에 적는다.
	 */
	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "warning_codes", nullable = false)
	private String[] warningCodes;

	/**
	 * 되돌리기(operation=REVERT)가 내용을 복사해 온 옛 판. REVERT 가 아니면 {@code null}.
	 */
	@Column(name = "reverted_from_version", updatable = false)
	private Integer revertedFromVersion;

	protected ItineraryVersionJpaEntity() {
		// JPA 전용
	}

	ItineraryVersionJpaEntity(UUID itineraryVersionId, UUID itineraryId, int version, Integer baseVersion,
			ItineraryVersion.Operation operation, UUID createdBy, String requestId, UUID sourceRequestId,
			String modelVersion, String featureVersion, String ontologyVersion,
			String policyVersion, String datasetVersion, OffsetDateTime createdAt, String[] warningCodes,
			Integer revertedFromVersion) {
		this.itineraryVersionId = itineraryVersionId;
		this.itineraryId = itineraryId;
		this.version = version;
		this.baseVersion = baseVersion;
		this.operation = operation;
		this.createdBy = createdBy;
		this.requestId = requestId;
		this.sourceRequestId = sourceRequestId;
		this.modelVersion = modelVersion;
		this.featureVersion = featureVersion;
		this.ontologyVersion = ontologyVersion;
		this.policyVersion = policyVersion;
		this.datasetVersion = datasetVersion;
		this.createdAt = createdAt;
		this.warningCodes = warningCodes;
		this.revertedFromVersion = revertedFromVersion;
	}

	UUID itineraryVersionId() { return itineraryVersionId; }
	UUID itineraryId() { return itineraryId; }
	int version() { return version; }
	Integer baseVersion() { return baseVersion; }
	ItineraryVersion.Operation operation() { return operation; }
	UUID createdBy() { return createdBy; }
	String requestId() { return requestId; }
	UUID sourceRequestId() { return sourceRequestId; }
	String modelVersion() { return modelVersion; }
	String featureVersion() { return featureVersion; }
	String ontologyVersion() { return ontologyVersion; }
	String policyVersion() { return policyVersion; }
	String datasetVersion() { return datasetVersion; }
	OffsetDateTime createdAt() { return createdAt; }
	String[] warningCodes() { return warningCodes; }
	Integer revertedFromVersion() { return revertedFromVersion; }
}
