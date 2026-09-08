package com.gabolle.backend.place.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 축제·행사가 실제로 열리는 기간 (S15P21E201-465).
 *
 * <p>한 장소에 회차가 여러 개일 수 있다 — 해마다 열리는 축제가 그렇다. 그래서 장소의 성질을
 * 담는 {@code place_feature} 가 아니라 별도 표다 (근거는 마이그레이션
 * {@code V20260907150000} 주석).
 *
 * <p>🔴 날짜는 {@link LocalDate} 다. 시각대가 없다. 축제는 "10월 1일부터 5일까지" 처럼 날짜로
 * 공고되고, 그것을 시각으로 바꾸면 없는 정보를 지어내게 된다. 여행 기간도 같은 이유로 날짜다.
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
	 * 이 회차가 주어진 기간과 겹치는가. 판정식은 <b>시작일 &le; 상대 종료일 그리고 종료일 &ge;
	 * 상대 시작일</b> 이다 — 완료 기준(-117)에 그대로 적혀 있다.
	 *
	 * <p>🔴 조회는 이 메서드를 쓰지 않고 SQL 에서 같은 식으로 거른다. 여기 있는 것은 그 SQL 이
	 * 맞는지 테스트가 대조할 기준이 필요해서다. 두 곳의 식이 어긋나면 테스트가 잡는다.
	 */
	public boolean overlaps(LocalDate from, LocalDate to) {
		return !this.startDate.isAfter(to) && !this.endDate.isBefore(from);
	}
}
