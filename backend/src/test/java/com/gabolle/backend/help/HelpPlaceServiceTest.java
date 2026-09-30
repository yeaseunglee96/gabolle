package com.gabolle.backend.help;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gabolle.backend.help.application.HelpPlaceCatalog;
import com.gabolle.backend.help.application.HelpPlaceService;
import com.gabolle.backend.help.domain.HelpKind;
import com.gabolle.backend.help.domain.HelpPlace;
import com.gabolle.backend.help.presentation.HelpPlaceController;
import com.gabolle.backend.help.presentation.HelpPlaceExceptionHandler;

/**
 * 가까운 도움(S15P21E201-1893) — 실제 자원 파일로 돈다. DB 없이.
 */
class HelpPlaceServiceTest {

	/** 광안리해수욕장 앞 */
	private static final double LAT = 35.1532;

	private static final double LNG = 129.1186;

	/** 2026-09-30(수) 부산 14:00 */
	private static final Clock WEDNESDAY_2PM = Clock.fixed(Instant.parse("2026-09-30T05:00:00Z"), ZoneId.of("UTC"));

	private static final HelpPlaceCatalog CATALOG = new HelpPlaceCatalog();

	private final HelpPlaceService service = new HelpPlaceService(CATALOG, WEDNESDAY_2PM);

	@Test
	@DisplayName("🔴 자료에 세 갈래가 다 있고, 약국이 OSM(부산 70곳)보다 훨씬 많다 — 이 기능을 서버로 옮긴 이유")
	void catalogHasAllKinds() {
		assertThat(CATALOG.places(HelpKind.PHARMACY)).hasSizeGreaterThan(1_000);
		assertThat(CATALOG.places(HelpKind.HOSPITAL)).hasSizeGreaterThan(1_000);
		assertThat(CATALOG.places(HelpKind.POLICE)).isNotEmpty();
		assertThat(CATALOG.source()).contains("건강보험심사평가원").contains("OpenStreetMap");
		assertThat(CATALOG.basedOn()).isEqualTo("2026-06-30");
	}

	@Test
	@DisplayName("🔴 여행자가 아플 때 갈 곳이 아닌 곳은 없다 — 요양·치과·한의원·피부·성형")
	void excludedTypesAreAbsent() {
		assertThat(CATALOG.places(HelpKind.HOSPITAL))
				.noneMatch(place -> place.name().matches(".*(요양|치과|한의|한방|피부|성형|미용).*"));
		assertThat(CATALOG.places(HelpKind.HOSPITAL)).extracting(HelpPlace::type)
				.allMatch(type -> type.matches("상급종합|종합병원|병원|의원|보건소|보건지소|보건진료소"));
	}

	@Test
	@DisplayName("광안리에서 약국은 걸어갈 거리에 있다 — 가까운 순으로 다섯 곳")
	void nearestPharmaciesAreClose() {
		HelpPlaceService.NearbyHelp result = this.service.nearby(HelpKind.PHARMACY, LAT, LNG, 5, false);
		assertThat(result.places()).hasSize(5);
		assertThat(result.places().get(0).distanceMeters()).isLessThan(500);
		assertThat(result.places()).isSortedAccordingTo((a, b) -> Double.compare(a.distanceMeters(), b.distanceMeters()));
	}

	@Test
	@DisplayName("🔴 부산 밖에서는 없다고 말한다 — 5km 밖은 「가까운 곳」이 아니다")
	void outsideBusanIsEmpty() {
		assertThat(this.service.nearby(HelpKind.HOSPITAL, 37.5665, 126.978, 5, false).places()).isEmpty();
	}

	@Test
	@DisplayName("지금 진료 중만 — 진료시간을 아는 곳 가운데 지금 여는 곳만")
	void openNowOnly() {
		HelpPlaceService.NearbyHelp result = this.service.nearby(HelpKind.HOSPITAL, LAT, LNG, 10, true);
		assertThat(result.places()).allMatch(found -> Boolean.TRUE.equals(found.today().openNow()));
	}

	@Test
	@DisplayName("오늘 진료시간 — 여는 시각 안이면 진료 중, 쉬는 날이면 false, 모르면 null")
	void todayHours() {
		HelpPlace place = new HelpPlace(HelpKind.HOSPITAL, "의원", "시험의원", null, null, null, LAT, LNG, false,
				Map.of(DayOfWeek.WEDNESDAY, new HelpPlace.DayHours(false, 900, 1830), DayOfWeek.SUNDAY, HelpPlace.DayHours.closedDay()));
		ZonedDateTime wednesday2pm = ZonedDateTime.of(2026, 9, 30, 14, 0, 0, 0, ZoneId.of("Asia/Seoul"));
		assertThat(HelpPlaceService.today(place, wednesday2pm)).isEqualTo(new HelpPlaceService.Today(900, 1830, true));
		assertThat(HelpPlaceService.today(place, wednesday2pm.withHour(19)).openNow()).isFalse();
		assertThat(HelpPlaceService.today(place, wednesday2pm.plusDays(4))).isEqualTo(new HelpPlaceService.Today(null, null, false));
		assertThat(HelpPlaceService.today(place, wednesday2pm.plusDays(1))).isEqualTo(new HelpPlaceService.Today(null, null, null));
	}

	@Test
	@DisplayName("좌표가 범위 밖이면 거부한다")
	void rejectsBadCoordinates() {
		assertThatThrownBy(() -> this.service.nearby(HelpKind.POLICE, 91, 0, 5, false)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("API — 갈래·좌표를 받아 가까운 곳과 출처를 준다. 모르는 갈래는 400")
	void http() throws Exception {
		MockMvc mvc = MockMvcBuilders.standaloneSetup(new HelpPlaceController(this.service))
				.setControllerAdvice(new HelpPlaceExceptionHandler())
				.build();
		mvc.perform(get("/api/v1/help-places/nearby").param("kind", "PHARMACY").param("lat", String.valueOf(LAT)).param("lng", String.valueOf(LNG)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.kind").value("PHARMACY"))
				.andExpect(jsonPath("$.data.places.length()").value(5))
				.andExpect(jsonPath("$.data.places[0].name").isString())
				.andExpect(jsonPath("$.data.places[0].distanceMeters").isNumber())
				.andExpect(jsonPath("$.data.basedOn").value("2026-06-30"));
		mvc.perform(get("/api/v1/help-places/nearby").param("kind", "HOSPITALS").param("lat", "35.1").param("lng", "129.1"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
		mvc.perform(get("/api/v1/help-places/nearby").param("kind", "POLICE").param("lat", "95").param("lng", "129.1"))
				.andExpect(status().isBadRequest());
	}
}
