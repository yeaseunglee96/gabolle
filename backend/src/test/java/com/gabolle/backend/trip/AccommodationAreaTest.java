package com.gabolle.backend.trip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.trip.application.PreferenceDefaultsService;
import com.gabolle.backend.trip.application.TripCreationService;
import com.gabolle.backend.trip.domain.Trip;
import com.gabolle.backend.trip.domain.TripConditionRules;
import com.gabolle.backend.trip.infra.InMemoryTripRepository;
import com.gabolle.backend.user.support.ConsentGuards;

/**
 * 묵는 동네가 {@code trip.accommodation_area} 에 남는가 — S15P21E201-1544.
 *
 * <p><b>왜 장소가 아니라 코드인가.</b> 앱의 숙소 칸은 검색어가 없을 때 추천 동네 넷을 먼저
 * 보여 주는데, 그 넷은 서버의 {@code TravelArea} 와 <b>좌표까지 같다.</b> 그것을 장소로도
 * 만들면 같은 「해운대」가 앱 어휘·서버 어휘·장소 행으로 <b>세 벌</b>이 되고 셋이 서로 조인이
 * 안 된다. {@code TravelArea} 주석이 그 상황을 이미 경고하고 있다.
 */
class AccommodationAreaTest {

	private static final Instant NOW = Instant.parse("2026-09-23T08:00:00Z");

	private InMemoryTripRepository repository;

	private TripCreationService service;

	@BeforeEach
	void setUp() {
		this.repository = new InMemoryTripRepository();
		Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
		this.service = new TripCreationService(this.repository, clock,
				new PreferenceDefaultsService(this.repository, clock), ConsentGuards.granting(),
				Optional.empty(), Optional.empty());
	}

	private TripCreationService.Command command(String accommodationArea) {
		return command(accommodationArea, LocalDate.of(2026, 9, 27));
	}

	/** 당일치기는 {@code finish} 를 가는 날(9/25)과 같게 준다 — 숙소 없이 만들어진다(S15P21E201-1585). */
	private TripCreationService.Command command(String accommodationArea, LocalDate finish) {
		return new TripCreationService.Command("usr_1",
				LocalDate.of(2026, 9, 25), finish,
				35.1587, 129.1604, 300000, 2, "MORNING_TO_EVENING", "Asia/Seoul",
				List.of(), List.of(), Trip.OwnerType.USER,
				null, false, false, false, null,
				List.of(), List.of(), null, accommodationArea);
	}

	private Trip create(String area, String key) {
		return save(command(area), key);
	}

	private Trip save(TripCreationService.Command command, String key) {
		return this.repository.findById(this.service.create(command, key).trip().tripId()).orElseThrow();
	}

	private static final LocalDate DAY_TRIP = LocalDate.of(2026, 9, 25);

	@Test
	@DisplayName("🔴 아는 동네 코드는 그대로 남는다 — 지금까지는 실을 자리가 없어 버려졌다")
	void aKnownAreaIsStored() {
		assertThat(create("HAEUNDAE", "key_1").accommodationArea()).isEqualTo("HAEUNDAE");
	}

	@Test
	@DisplayName("소문자로 와도 서버 어휘로 맞춰 적는다 — 같은 동네가 두 표기로 갈리면 안 된다")
	void theCodeIsNormalised() {
		assertThat(create("haeundae", "key_2").accommodationArea()).isEqualTo("HAEUNDAE");
	}

	@Test
	@DisplayName("🔴 모르는 코드가 와도 «여행 생성이 안 막힌다» — 그 칸만 빈다 (당일치기)")
	void anUnknownAreaDoesNotBlockCreation() {
		// 1박 이상이면 모르는 코드만으로는 숙소가 없는 것이라 거부된다 — 아래 시험. 당일치기는 숙소가 필요 없다.
		Trip saved = save(command("GIJANG_NEW_2027", DAY_TRIP), "key_3");

		assertThat(saved.accommodationArea())
				.as("앱이 새 지역을 먼저 내보내는 날 여행 생성이 400 이 되면 안 된다")
				.isNull();
		assertThat(saved.tripId()).isNotNull();
	}

	@Test
	@DisplayName("안 보내면 비어 있다 — 당일치기는 숙소가 필요 없어 오류가 아니다")
	void noAreaIsFine() {
		assertThat(save(command(null, DAY_TRIP), "key_4").accommodationArea()).isNull();
	}

	/**
	 * 모르는 코드는 저장할 때 버려진다. 여러 날 여행에서 그것만 왔다면 숙소가 없는 여행이 되고, 그런 여행은 추천을
	 * 받을 수 없다(S15P21E201-1585) — 만들어 놓고 추천을 못 받는 여행을 남기느니 만들 때 말한다.
	 */
	@Test
	@DisplayName("🔴 여러 날 여행에 모르는 코드만 오면 숙소가 없는 것이라 거부된다")
	void anUnknownAreaAloneIsNoLodgingForAMultiDayTrip() {
		assertThatThrownBy(() -> create("GIJANG_NEW_2027", "key_6"))
				.isInstanceOf(TripConditionRules.TripConditionRejectedException.class)
				.hasMessageStartingWith("accommodation: ");
	}

	@Test
	@DisplayName("🔴 숙소 장소와 «겹치지 않는다» — 정확한 숙소와 묵는 동네는 다른 사실이다")
	void theAreaIsIndependentOfThePlace() {
		TripCreationService.Command both = new TripCreationService.Command("usr_1",
				LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 27),
				35.1587, 129.1604, 300000, 2, "MORNING_TO_EVENING", "Asia/Seoul",
				List.of(), List.of(), Trip.OwnerType.USER,
				"11111111-2222-3333-4444-555555555555", false, false, false, null,
				List.of(), List.of(), null, "GWANGALLI");

		Trip saved = this.repository.findById(this.service.create(both, "key_5").trip().tripId()).orElseThrow();

		assertThat(saved.accommodationPlaceId()).isEqualTo("11111111-2222-3333-4444-555555555555");
		assertThat(saved.accommodationArea()).isEqualTo("GWANGALLI");
	}
}
