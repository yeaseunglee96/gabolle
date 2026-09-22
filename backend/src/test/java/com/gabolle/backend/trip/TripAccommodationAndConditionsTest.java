package com.gabolle.backend.trip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.trip.domain.Trip;

/** {@link Trip} 의 숙소·영어메뉴·해외카드·혼밥우선·최대환승 다섯 칸. */
class TripAccommodationAndConditionsTest {

	private static final Instant NOW = Instant.parse("2026-09-09T00:00:00Z");

	@Test
	@DisplayName("옛 14-인자 생성자로 만들면 새 다섯 칸은 기본값이다 - 호출부를 안 건드려도 된다")
	void legacyConstructorDefaultsNewFields() {
		Trip trip = new Trip("trip_1", "usr_1",
				LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 12),
				null, null, null, 1, null, "Asia/Seoul",
				new String[0], null, null, NOW);

		assertNull(trip.accommodationPlaceId());
		assertFalse(trip.englishMenuRequired());
		assertFalse(trip.foreignCardRequired());
		assertFalse(trip.soloFriendlyPriority());
		assertNull(trip.maxTransitTransfers());
	}

	@Test
	@DisplayName("숙소·선호·최대환승을 새 생성자로 넣으면 그대로 보관된다")
	void fullConstructorKeepsAllFields() {
		Trip trip = new Trip("trip_2", "usr_1",
				LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 12),
				null, null, null, 1, null, "Asia/Seoul",
				new String[] { "SUBWAY" }, null, null,
				"place_1", true, true, true, 2, NOW);

		assertEquals("place_1", trip.accommodationPlaceId());
		assertEquals(true, trip.englishMenuRequired());
		assertEquals(true, trip.foreignCardRequired());
		assertEquals(true, trip.soloFriendlyPriority());
		assertEquals(2, trip.maxTransitTransfers());
	}

	@Test
	@DisplayName("최대 환승 횟수가 음수면 거부된다")
	void negativeMaxTransitTransfersIsRejected() {
		assertThrows(IllegalArgumentException.class, () -> new Trip("trip_3", "usr_1",
				LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 12),
				null, null, null, 1, null, "Asia/Seoul",
				new String[0], null, null,
				null, false, false, false, -1, NOW));
	}

	@Test
	@DisplayName("Builder 로 되살릴 때도 다섯 칸이 그대로 되살아난다")
	void builderRestoresAllFields() {
		Trip trip = Trip.builder()
				.tripId("trip_4").createdBy("usr_1")
				.startDate(LocalDate.of(2026, 9, 10)).finishDate(LocalDate.of(2026, 9, 12))
				.partySize(1).timezone("Asia/Seoul")
				.accommodationPlaceId("place_2")
				.englishMenuRequired(true)
				.foreignCardRequired(false)
				.soloFriendlyPriority(true)
				.maxTransitTransfers(1)
				.createdAt(NOW)
				.build();

		assertEquals("place_2", trip.accommodationPlaceId());
		assertEquals(true, trip.englishMenuRequired());
		assertEquals(false, trip.foreignCardRequired());
		assertEquals(true, trip.soloFriendlyPriority());
		assertEquals(1, trip.maxTransitTransfers());
	}
}
