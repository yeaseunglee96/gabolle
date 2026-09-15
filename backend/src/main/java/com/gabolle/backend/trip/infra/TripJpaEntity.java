package com.gabolle.backend.trip.infra;

import java.time.LocalDate;
import java.time.LocalTime;
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

import com.gabolle.backend.trip.domain.Trip;

/**
 * {@code trip} 표 매핑 — S15P21E201-461.
 *
 * <p>🔴 {@link Trip}(도메인) 은 JPA 를 모른다. 이 클래스가 그 경계를 대신 진다 —
 * {@link JpaTripRepository} 가 여기서만 변환한다.
 *
 * <p>🔴 {@code version}·{@code origin_source}·{@code origin_area_code} 컬럼은 여기서
 * <b>일부러</b> 매핑하지 않는다. 도메인 {@link Trip} 에 아직 그 값이 없고, 지어낸 값을
 * 넣으면 그 결정을 여기서 대신 내리는 셈이 된다(V120000 마이그레이션과 같은 원칙).
 * 매핑하지 않으면 INSERT 문에 그 칸이 아예 안 실리고, DB 의 DEFAULT(1)가 대신 채운다.
 *
 * <p>🔴 {@code travel_modes}·{@code time_window_start}·{@code time_window_end} 는
 * S15P21E201-604 가 매핑을 더했다 — 추천 엔진이 읽어야 한다. {@code time_window}(프리셋)와
 * {@code time_window_preset} 은 여전히 건드리지 않는다(같은 사실을 말하는 칸 정리는 별도 티켓).
 */
@Entity
@Table(name = "trip")
public class TripJpaEntity {

	@Id
	@Column(name = "trip_id")
	private UUID tripId;

	/**
	 * 🔴 S15P21E201-317 — {@code updatable = false} 다. 소유자는 여행 생성 이후 바뀌지 않는다는
	 * 뜻이었는데, 딱 하나(익명 세션 승계) 예외가 생겼다. 그 예외는 이 엔티티를 고쳐 저장하는
	 * 경로가 아니라 {@link JpaTripRepository#claimAnonymousTrips} 의 네이티브 SQL 로만 이뤄진다 —
	 * 그래서 이 플래그는 그대로 둔다("보통은 안 바뀐다"는 사실은 여전히 참이다).
	 */
	@Column(name = "owner_user_id", nullable = false, updatable = false)
	private UUID ownerUserId;

	/** {@code USER} | {@code ANONYMOUS}. {@link com.gabolle.backend.trip.domain.Trip.OwnerType} 과 1:1. */
	@Column(name = "owner_type", nullable = false, updatable = false, length = 20)
	private String ownerType;

	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	@Column(name = "end_date", nullable = false)
	private LocalDate endDate;

	@Column(name = "origin_lat")
	private Double originLat;

	@Column(name = "origin_lng")
	private Double originLng;

	@Column(name = "budget_krw")
	private Long budgetKrw;

	@Column(name = "party_size")
	private Integer partySize;

	/** 하루 활동 시간대 프리셋. 예: {@code MORNING_TO_EVENING} — V160000 이 추가한 칸. */
	@Column(name = "time_window")
	private String timeWindow;

	@Column(name = "time_window_start")
	private LocalTime timeWindowStart;

	@Column(name = "time_window_end")
	private LocalTime timeWindowEnd;

	/** {@code ck_trip_travel_modes} 의 아홉 개가 값 목록의 정본이다. */
	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "travel_modes")
	private String[] travelModes;

	/** 매일 여기서 시작하고 여기로 돌아온다 (S15P21E201-456). {@code place} FK. */
	@Column(name = "accommodation_place_id")
	private UUID accommodationPlaceId;

	@Column(name = "english_menu_required", nullable = false)
	private boolean englishMenuRequired;

	@Column(name = "foreign_card_required", nullable = false)
	private boolean foreignCardRequired;

	@Column(name = "solo_friendly_priority", nullable = false)
	private boolean soloFriendlyPriority;

	/** {@code null} 이면 제한 없음. {@code PRIVATE_CAR} 가 travelModes 에 있으면 저장 시점에 이미 null 이다. */
	@Column(name = "max_transit_transfers")
	private Integer maxTransitTransfers;

	@Column(name = "timezone", nullable = false)
	private String timezone;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private Trip.Status status;

	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	@Column(name = "deleted_at")
	private OffsetDateTime deletedAt;

	protected TripJpaEntity() {
		// JPA 전용
	}

	TripJpaEntity(UUID tripId, UUID ownerUserId, String ownerType, LocalDate startDate, LocalDate endDate,
			Double originLat, Double originLng, Long budgetKrw, Integer partySize,
			String timeWindow, String timezone, String[] travelModes,
			LocalTime timeWindowStart, LocalTime timeWindowEnd,
			UUID accommodationPlaceId, boolean englishMenuRequired, boolean foreignCardRequired,
			boolean soloFriendlyPriority, Integer maxTransitTransfers, Trip.Status status,
			OffsetDateTime createdAt, OffsetDateTime updatedAt, OffsetDateTime deletedAt) {
		this.tripId = tripId;
		this.ownerUserId = ownerUserId;
		this.ownerType = ownerType;
		this.startDate = startDate;
		this.endDate = endDate;
		this.originLat = originLat;
		this.originLng = originLng;
		this.budgetKrw = budgetKrw;
		this.partySize = partySize;
		this.timeWindow = timeWindow;
		this.timeWindowStart = timeWindowStart;
		this.timeWindowEnd = timeWindowEnd;
		this.travelModes = travelModes;
		this.accommodationPlaceId = accommodationPlaceId;
		this.englishMenuRequired = englishMenuRequired;
		this.foreignCardRequired = foreignCardRequired;
		this.soloFriendlyPriority = soloFriendlyPriority;
		this.maxTransitTransfers = maxTransitTransfers;
		this.timezone = timezone;
		this.status = status;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
		this.deletedAt = deletedAt;
	}

	/**
	 * 지운 시각을 찍는다 — S15P21E201-746. {@code status} 는 건드리지 않는다(도메인
	 * {@link Trip#markDeleted} 와 같은 이유 — 지워지기 전에 어느 단계였는지가 남아야 한다).
	 *
	 * <p>🔴 값을 여기서 만들지 않고 <b>인자로 받는다.</b> 이 클래스가 지금 시각을 읽으면
	 * 도메인이 정한 시각과 미세하게 어긋나고, 그러면 응답에 실린 시각과 표에 남은 시각이
	 * 다른 값이 된다.
	 */
	void markDeleted(OffsetDateTime deletedAt, OffsetDateTime updatedAt) {
		this.deletedAt = deletedAt;
		this.updatedAt = updatedAt;
	}

	/**
	 * 상태 칸을 옮긴다 — S15P21E201-964. 어느 상태로 갈 수 있는지는 도메인
	 * ({@link Trip#markReady}) 이 이미 판정했고 여기서는 옮겨 적기만 한다.
	 *
	 * <p>{@link #markDeleted} 와 같이 시각을 인자로 받는다 — 같은 이유다.
	 */
	void changeStatus(Trip.Status status, OffsetDateTime updatedAt) {
		this.status = status;
		this.updatedAt = updatedAt;
	}

	UUID tripId() { return tripId; }
	UUID ownerUserId() { return ownerUserId; }
	String ownerType() { return ownerType; }
	LocalDate startDate() { return startDate; }
	LocalDate endDate() { return endDate; }
	Double originLat() { return originLat; }
	Double originLng() { return originLng; }
	Long budgetKrw() { return budgetKrw; }
	Integer partySize() { return partySize; }
	String timeWindow() { return timeWindow; }
	LocalTime timeWindowStart() { return timeWindowStart; }
	LocalTime timeWindowEnd() { return timeWindowEnd; }
	String[] travelModes() { return travelModes; }
	UUID accommodationPlaceId() { return accommodationPlaceId; }
	boolean englishMenuRequired() { return englishMenuRequired; }
	boolean foreignCardRequired() { return foreignCardRequired; }
	boolean soloFriendlyPriority() { return soloFriendlyPriority; }
	Integer maxTransitTransfers() { return maxTransitTransfers; }
	String timezone() { return timezone; }
	Trip.Status status() { return status; }
	OffsetDateTime createdAt() { return createdAt; }
	OffsetDateTime updatedAt() { return updatedAt; }
	OffsetDateTime deletedAt() { return deletedAt; }
}
