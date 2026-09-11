package com.gabolle.backend.trip;

import com.gabolle.backend.user.support.ConsentGuards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.trip.application.PreferenceDefaultsService;
import com.gabolle.backend.trip.application.TripCreationService;
import com.gabolle.backend.trip.domain.PreferenceSnapshot;
import com.gabolle.backend.trip.domain.Trip;
import com.gabolle.backend.trip.infra.InMemoryTripRepository;

/**
 * S15P21E201-456 — 숙소·영어메뉴/해외카드/혼밥우선 선호·최대환승횟수 저장.
 *
 * <p>완료 기준 "저장한 조건을 다시 읽으면 고른 값이 그대로 있다"와, 자차(PRIVATE_CAR)
 * 이동이면 최대 환승 횟수를 무시하는 규칙을 검증한다.
 */
class TripCreationServiceAccommodationTest {

	private static final Instant NOW = Instant.parse("2026-09-09T00:00:00Z");

	private InMemoryTripRepository repository;
	private TripCreationService service;

	@BeforeEach
	void setUp() {
		repository = new InMemoryTripRepository();
		Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
		service = new TripCreationService(repository, clock, new PreferenceDefaultsService(repository, clock), ConsentGuards.granting());
	}

	private TripCreationService.Command command(String accommodationPlaceId, boolean englishMenu,
			boolean foreignCard, boolean soloFriendly, Integer maxTransfers, String[] transport) {
		List<PreferenceSnapshot.PreferenceAnswer> preferences = transport == null ? List.of()
				: List.of(new PreferenceSnapshot.PreferenceAnswer(
						"transport", "\"" + transport[0] + "\"", PreferenceSnapshot.AnswerStatus.SELECTED));

		return new TripCreationService.Command(
				"usr_1",
				LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 12),
				35.1587, 129.1604,
				300000, 2,
				"MORNING_TO_EVENING", "Asia/Seoul",
				preferences,
				List.of(),
				accommodationPlaceId, englishMenu, foreignCard, soloFriendly, maxTransfers);
	}

	@Test
	@DisplayName("숙소·영어메뉴·해외카드·혼밥우선·최대환승을 저장하면 그대로 다시 읽힌다")
	void savesAndReadsBackAccommodationAndConditions() {
		String placeId = UUID.randomUUID().toString();

		var result = service.create(command(placeId, true, true, true, 2, null), "key_1");

		Trip saved = repository.findById(result.trip().tripId()).orElseThrow();
		assertEquals(placeId, saved.accommodationPlaceId());
		assertTrue(saved.englishMenuRequired());
		assertTrue(saved.foreignCardRequired());
		assertTrue(saved.soloFriendlyPriority());
		assertEquals(2, saved.maxTransitTransfers());
	}

	@Test
	@DisplayName("안 보내면 우선하지 않는 것으로, 최대환승은 제한 없음으로 저장된다")
	void defaultsToFalseAndUnlimited() {
		var result = service.create(command(null, false, false, false, null, null), "key_2");

		Trip saved = repository.findById(result.trip().tripId()).orElseThrow();
		assertNull(saved.accommodationPlaceId());
		assertFalse(saved.englishMenuRequired());
		assertFalse(saved.foreignCardRequired());
		assertFalse(saved.soloFriendlyPriority());
		assertNull(saved.maxTransitTransfers());
	}

	@Test
	@DisplayName("자차(PRIVATE_CAR) 이동이면 최대 환승 횟수를 무시한다 - 자차에는 환승 개념이 없다")
	void ignoresMaxTransitTransfersWhenPrivateCar() {
		// 🔴 "transport" 취향 답은 화면 값(WALK/CAR/TRANSIT)이고, TravelModes 가 그것을
		// travel_modes 코드(PRIVATE_CAR 등)로 바꾼다 — CAR 를 보내면 PRIVATE_CAR 가 된다.
		var result = service.create(
				command(null, false, false, false, 3, new String[] { "CAR" }), "key_3");

		Trip saved = repository.findById(result.trip().tripId()).orElseThrow();
		assertNull(saved.maxTransitTransfers(), "자차 이동인데 환승 횟수가 저장되면 안 된다");
	}

	@Test
	@DisplayName("대중교통 이동이면 최대 환승 횟수를 그대로 저장한다")
	void keepsMaxTransitTransfersWhenNotPrivateCar() {
		var result = service.create(
				command(null, false, false, false, 3, new String[] { "TRANSIT" }), "key_4");

		Trip saved = repository.findById(result.trip().tripId()).orElseThrow();
		assertEquals(3, saved.maxTransitTransfers());
	}
}
