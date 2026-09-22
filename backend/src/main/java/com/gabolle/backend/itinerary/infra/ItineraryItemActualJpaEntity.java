package com.gabolle.backend.itinerary.infra;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * {@code itinerary_item_actual} 표 매핑.
 * 이 엔티티는 읽기 전용이다. 쓰기는 {@link JpaItineraryItemActualRepository} 의
 * {@code INSERT ... ON CONFLICT DO UPDATE} 가 담당한다 — 같은 방문지에 두 번 보내는 것이 정상
 * 경로라서 "찾아서 없으면 넣고 있으면 고친다" 로 하면 두 요청이 겹칠 때 UNIQUE 위반이 나고,
 * PostgreSQL 은 문장 하나가 실패하면 그 트랜잭션 전체를 못 쓰게 만든다.
 * {@link ItineraryExclusionJpaEntity} 와 달리 {@code updatable=false} 를 붙이지 않는다. 그
 * 표는 판마다 새 행을 쓰는 스냅샷이지만 이 표는 같은 행을 고치는 것이 기능이다 — 다만 그
 * 갱신을 Hibernate 의 변경 감지가 아니라 위 SQL 이 한다.
 */
@Entity
@Table(name = "itinerary_item_actual")
public class ItineraryItemActualJpaEntity {

	@Id
	@Column(name = "itinerary_item_actual_id")
	private UUID itineraryItemActualId;

	@Column(name = "itinerary_id", nullable = false)
	private UUID itineraryId;

	/** {@code itinerary_item_id} 가 아니다 — 편집할 때마다 항목 행이 복사되기 때문이다. */
	@Column(name = "item_key", nullable = false)
	private UUID itemKey;

	@Column(name = "arrived_at")
	private OffsetDateTime arrivedAt;

	@Column(name = "departed_at")
	private OffsetDateTime departedAt;

	@Column(name = "recorded_by", nullable = false)
	private UUID recordedBy;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected ItineraryItemActualJpaEntity() {
		// JPA 전용
	}

	UUID itineraryItemActualId() { return itineraryItemActualId; }
	UUID itineraryId() { return itineraryId; }
	UUID itemKey() { return itemKey; }
	OffsetDateTime arrivedAt() { return arrivedAt; }
	OffsetDateTime departedAt() { return departedAt; }
	UUID recordedBy() { return recordedBy; }
	OffsetDateTime createdAt() { return createdAt; }
	OffsetDateTime updatedAt() { return updatedAt; }
}
