package com.gabolle.backend.place.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 축제·행사가 실제로 열리는 기간. 한 장소에 회차가 여러 개일 수 있어(해마다 열리는 축제)
 * 장소의 성질을 담는 {@code place_feature} 가 아니라 별도 표다.
 *
 * <p>날짜는 시각대 없는 {@link LocalDate} 다. 축제는 "10월 1일부터 5일까지" 처럼 날짜로 공고되고,
 * 그것을 시각으로 바꾸면 없는 정보를 지어내게 된다.
 */
@Entity
@Table(name = "place_event_period")
public class PlaceEventPeriod {

	@Id
	@Column(name = "place_event_period_id", nullable = false, updatable = false)
	private UUID placeEventPeriodId;

	@Column(name = "place_id", nullable = false)
	private UUID placeId;

	/** 회차 이름. 없으면 화면은 장소 이름을 쓴다. */
	@Column(name = "title", length = 200)
	private String title;

	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	@Column(name = "end_date", nullable = false)
	private LocalDate endDate;

	@Column(name = "source_type", length = 50)
	private String sourceType;

	@Column(name = "source_id", length = 200)
	private String sourceId;

	@Column(name = "observed_at")
	private OffsetDateTime observedAt;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	protected PlaceEventPeriod() {
	}

	private PlaceEventPeriod(UUID placeEventPeriodId, UUID placeId, String title, LocalDate startDate,
			LocalDate endDate, String sourceType, String sourceId, OffsetDateTime observedAt) {
		this.placeEventPeriodId = placeEventPeriodId;
		this.placeId = placeId;
		this.title = title;
		this.startDate = startDate;
		this.endDate = endDate;
		this.sourceType = sourceType;
		this.sourceId = sourceId;
		this.observedAt = observedAt;
	}

	/**
	 * 회차 한 건을 만든다. 식별자를 출처에서 계산하므로 같은 자료를 다시 적재하면 같은 id 로 와서
	 * 덮어쓰기가 된다 — 무작위로 만들면 유일 제약({@code uq_place_event_period}) 위반으로 적재가 멈춘다.
	 *
	 * <p>기간은 지어내지 않는다. 시작일이나 종료일을 모르는 축제는 이 메서드를 부르지 않는 것으로
	 * 표현한다 — 날짜를 추측해 채우면 사람이 안 열리는 축제를 보러 간다.
	 */
	public static PlaceEventPeriod of(UUID placeId, String title, LocalDate startDate, LocalDate endDate,
			String sourceType, String sourceId, OffsetDateTime observedAt) {
		if (placeId == null) {
			throw new IllegalArgumentException("어느 장소의 회차인지 없이 만들 수 없다");
		}
		if (startDate == null || endDate == null) {
			throw new IllegalArgumentException("기간을 모르는 회차는 행으로 만들지 않는다");
		}
		if (endDate.isBefore(startDate)) {
			throw new IllegalArgumentException("종료일이 시작일보다 빠를 수 없다: " + startDate + " ~ " + endDate);
		}
		return new PlaceEventPeriod(idOf(placeId, startDate, endDate), placeId, title, startDate, endDate,
				sourceType, sourceId, observedAt);
	}

	/**
	 * 같은 회차는 언제 적재해도 같은 id 를 갖는다. 재료가 표의 유일 제약과 같은 세 값이어야 한다 —
	 * 다른 조합을 쓰면 제약은 막는데 id 는 달라서 적재가 제약 위반으로 죽는다.
	 */
	private static UUID idOf(UUID placeId, LocalDate startDate, LocalDate endDate) {
		return UUID.nameUUIDFromBytes(("place-event-period|" + placeId + "|" + startDate + "|" + endDate)
				.getBytes(java.nio.charset.StandardCharsets.UTF_8));
	}

	public UUID getPlaceEventPeriodId() {
		return this.placeEventPeriodId;
	}

	public UUID getPlaceId() {
		return this.placeId;
	}

	public String getTitle() {
		return this.title;
	}

	public LocalDate getStartDate() {
		return this.startDate;
	}

	public LocalDate getEndDate() {
		return this.endDate;
	}

	public String getSourceType() {
		return this.sourceType;
	}

	public String getSourceId() {
		return this.sourceId;
	}

	public OffsetDateTime getObservedAt() {
		return this.observedAt;
	}

	public OffsetDateTime getCreatedAt() {
		return this.createdAt;
	}

	/**
	 * 이 회차가 주어진 기간과 겹치는가. 조회는 이 메서드를 쓰지 않고 SQL 에서 같은 식으로 거른다 —
	 * 여기 있는 것은 그 SQL 이 맞는지 테스트가 대조할 기준이다.
	 */
	public boolean overlaps(LocalDate from, LocalDate to) {
		return !this.startDate.isAfter(to) && !this.endDate.isBefore(from);
	}
}
