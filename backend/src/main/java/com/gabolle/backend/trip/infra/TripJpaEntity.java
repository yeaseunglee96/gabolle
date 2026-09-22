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
 * {@code trip} 표 매핑. 도메인 {@link Trip} 은 JPA 를 모르고, 변환은 {@link JpaTripRepository}
 * 가 여기서만 한다.
 *
 * <p>{@code version}·{@code origin_source}·{@code origin_area_code} 는 일부러 매핑하지 않는다 —
 * 도메인에 그 값이 없어서 여기서 지어내면 결정을 대신 내리는 셈이다. 안 매핑하면 INSERT 에
 * 칸이 안 실리고 DB 의 DEFAULT 가 채운다. 이 칸들이 매핑 안 된 상태이므로, 이 엔티티를 통째로
 * 덮어쓰는 저장은 하지 않는다.
 */
@Entity
@Table(name = "trip")
public class TripJpaEntity {

	@Id
	@Column(name = "trip_id")
	private UUID tripId;

	/**
	 * {@code updatable = false} — 소유자는 생성 이후 안 바뀐다. 예외는 익명 세션 승계 하나뿐이고,
	 * 그건 {@link JpaTripRepository#claimAnonymousTrips} 의 네이티브 SQL 로만 이뤄진다.
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

	/** 하루 활동 시간대 프리셋. 예: {@code MORNING_TO_EVENING}. */
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

	/** 매일 여기서 시작하고 여기로 돌아온다. {@code place} FK. */
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

	/** 여행 기분. 안 고른 여행은 {@code null} 이다 — 「보통」이 아니라 「모른다」다. */
	@Column(name = "pace", length = 16)
	private String pace;

	@Column(name = "timezone", nullable = false)
	private String timezone;

	/**
	 * 사용자가 붙인 이름. {@code null} 이면 아직 이름이 없다. 길이 60 은 마이그레이션
	 * {@code V20260915140000__trip_title.sql} 과 같은 값이어야 한다.
	 */
	@Column(name = "title", length = 60)
	private String title;

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
			boolean soloFriendlyPriority, Integer maxTransitTransfers, String pace, String title, Trip.Status status,
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
		this.pace = pace;
		this.timezone = timezone;
		this.title = title;
		this.status = status;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
		this.deletedAt = deletedAt;
	}

	/**
	 * 지운 시각을 찍는다. {@code status} 는 건드리지 않는다 — 지워지기 전에 어느 단계였는지가
	 * 남아야 한다.
	 *
	 * <p>시각을 여기서 읽지 않고 인자로 받는다. 직접 읽으면 도메인이 정한 시각과 어긋나서
	 * 응답에 실린 시각과 표에 남은 시각이 달라진다.
	 */
	void markDeleted(OffsetDateTime deletedAt, OffsetDateTime updatedAt) {
		this.deletedAt = deletedAt;
		this.updatedAt = updatedAt;
	}

	/** 어느 상태로 갈 수 있는지는 도메인({@link Trip#markReady})이 판정했고 여기서는 옮겨 적기만 한다. */
	void changeStatus(Trip.Status status, OffsetDateTime updatedAt) {
		this.status = status;
		this.updatedAt = updatedAt;
	}

	/** 길이·제어문자 규칙은 도메인({@link Trip#rename})이 판정했고 여기서는 옮겨 적기만 한다. */
	void changeTitle(String title, OffsetDateTime updatedAt) {
		this.title = title;
		this.updatedAt = updatedAt;
	}

	UUID tripId() { return tripId; }
	String title() { return title; }
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
	String pace() { return pace; }
	String timezone() { return timezone; }
	Trip.Status status() { return status; }
	OffsetDateTime createdAt() { return createdAt; }
	OffsetDateTime updatedAt() { return updatedAt; }
	OffsetDateTime deletedAt() { return deletedAt; }
}
