package com.gabolle.backend.place;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gabolle.backend.place.repository.PlaceRepository;
import com.gabolle.backend.place.support.PlacePostgresIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 「여행 기간에 하루도 안 여는 행사 장소」 질의 (S15P21E201-1618). 추천 엔진이 이것으로 날짜가 안 맞는 축제를 후보에서
 * 뺀다. 일정 조립의 판정(여행 날짜 중 하루라도 회차 기간 안이면 연다)과 같아야 한다.
 */
class EventPlacesClosedQueryTest extends PlacePostgresIntegrationTest {

	private static final LocalDate TRIP_FROM = LocalDate.of(2026, 10, 3);

	private static final LocalDate TRIP_TO = LocalDate.of(2026, 10, 5);

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PlaceRepository placeRepository;

	private final List<UUID> seeded = new ArrayList<>();

	@AfterEach
	void cleanUp() {
		this.seeded.forEach((placeId) -> this.jdbc.update("DELETE FROM place WHERE place_id = ?", placeId));
		this.seeded.clear();
	}

	@Test
	@DisplayName("🔴 여행 기간에 하루도 안 여는 곳만 나온다 — 경계 날짜가 겹치면 연다")
	void onlyPlacesClosedThroughoutTheTripAreReturned() {
		UUID ended = place(period(9, 20, 9, 25));
		UUID touchesFirstDay = place(period(9, 28, 10, 3));
		UUID touchesLastDay = place(period(10, 5, 10, 9));
		UUID secondRoundOpen = place(period(9, 1, 9, 2), period(10, 4, 10, 4));
		UUID noPeriods = place();

		List<UUID> closed = this.placeRepository.findEventPlacesClosedThroughout(
				List.of(ended, touchesFirstDay, touchesLastDay, secondRoundOpen, noPeriods), TRIP_FROM, TRIP_TO);

		assertThat(closed).containsExactly(ended);
	}

	private record Period(LocalDate from, LocalDate to) {
	}

	private static Period period(int fromMonth, int fromDay, int toMonth, int toDay) {
		return new Period(LocalDate.of(2026, fromMonth, fromDay), LocalDate.of(2026, toMonth, toDay));
	}

	private UUID place(Period... periods) {
		UUID placeId = UUID.randomUUID();
		this.jdbc.update("""
				INSERT INTO place (place_id, name_ko, category, lat, lng, source_type, source_id, created_at)
				VALUES (?, '축제', 'FESTIVAL_EVENT', 35.15, 129.11, 'TEST', ?, now())
				""", placeId, "t-1618-" + placeId);
		this.seeded.add(placeId);
		for (Period period : periods) {
			this.jdbc.update("""
					INSERT INTO place_event_period (place_event_period_id, place_id, start_date, end_date)
					VALUES (?, ?, ?, ?)
					""", UUID.randomUUID(), placeId, period.from(), period.to());
		}
		return placeId;
	}
}
