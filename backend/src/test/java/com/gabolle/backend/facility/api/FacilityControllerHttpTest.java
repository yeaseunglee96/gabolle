package com.gabolle.backend.facility.api;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Limit;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gabolle.backend.facility.domain.Facility;
import com.gabolle.backend.facility.domain.FacilityKind;
import com.gabolle.backend.facility.domain.ParkingFee;
import com.gabolle.backend.facility.domain.WeeklyHours;
import com.gabolle.backend.facility.domain.WeeklyHours.Window;
import com.gabolle.backend.facility.repository.FacilityRepository;
import com.gabolle.backend.facility.service.FacilityNearbyService;

/**
 * 근처 편의시설 끝점의 JSON 모양과 400 응답 (S15P21E201-2052) — DB 없이 실제 직렬화를 거친다. 거르고 정렬하는 규칙은
 * {@code FacilityNearbyServiceTest}, 진짜 PostgreSQL 의 경계상자 질의는 {@code FacilityNearbyIntegrationTest} 가 본다.
 *
 * <p>특히 «모른다»가 칸째 빠지지 않고 {@code null} 로 실려 가는지, 종류마다 다른 {@code detail} 이 풀려 실리는지는 직렬화를 거쳐야 안다.
 */
class FacilityControllerHttpTest {

	private static final double LAT = 35.1;

	private static final double LNG = 129.0;

	private final FacilityRepository repository = mock(FacilityRepository.class);

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		// 서울 2026-10-14(수) 10:30
		Clock clock = Clock.fixed(Instant.parse("2026-10-14T01:30:00Z"), ZoneOffset.UTC);
		FacilityController controller = new FacilityController(new FacilityNearbyService(this.repository, clock));
		this.mockMvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new FacilityExceptionHandler())
				.build();
	}

	private static Map<String, Object> mapOf(Object... keyAndValue) {
		Map<String, Object> map = new LinkedHashMap<>();
		for (int i = 0; i < keyAndValue.length; i += 2) {
			map.put((String) keyAndValue[i], keyAndValue[i + 1]);
		}
		return map;
	}

	private static Facility facility(FacilityKind kind, String name, double metersNorth, Map<String, Object> detail) {
		return Facility.create(kind, name, name, "부산광역시 " + name, LAT + metersNorth / 111_194.9, LNG, detail, "test",
				OffsetDateTime.parse("2026-10-14T00:00:00Z"));
	}

	private void returns(FacilityKind kind, Facility... found) {
		when(this.repository.findInBox(eq(kind), anyDouble(), anyDouble(), anyDouble(), anyDouble(), any(Limit.class)))
				.thenReturn(List.of(found));
	}

	@Test
	@DisplayName("🔴 주차장 응답: 공통 칸 + detail, 무료는 0 · 모르는 요금은 null(칸째 빠지지 않는다) · 예상요금 순서")
	void parkingJson() throws Exception {
		Window eightToTwenty = new Window(8 * 60, 20 * 60);
		returns(FacilityKind.PARKING,
				facility(FacilityKind.PARKING, "요금모름", 100,
						mapOf("operator", "PUBLIC", "free", false, "spaces", 30, "fee", null, "hours", null, "note",
								"2번 출구 · 부산시설공단 · T.860-7713")),
				facility(FacilityKind.PARKING, "싼곳", 200,
						mapOf("operator", "PRIVATE", "free", false, "spaces", 12, "fee",
								new ParkingFee(30, 300, 10, 100, 2400, 0).toDetail(), "hours",
								WeeklyHours.of(Map.of("Wednesday", eightToTwenty)).toDetail(), "note", null)));

		this.mockMvc.perform(get("/api/v1/facilities/nearby").param("kind", "PARKING").param("lat", "35.1")
				.param("lng", "129.0").param("stayMinutes", "120")).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.kind").value("PARKING"))
				.andExpect(jsonPath("$.data.radiusM").value(700))
				.andExpect(jsonPath("$.data.stayMinutes").value(120))
				.andExpect(jsonPath("$.data.total").value(2))
				.andExpect(jsonPath("$.data.limit").value(20))
				.andExpect(jsonPath("$.data.asOf").value("2026-10-14T10:30:00+09:00"))
				.andExpect(jsonPath("$.meta.requestId").exists())
				// 요금을 아는 곳이 먼저 — 2시간은 300 + 9×100 = 1200원
				.andExpect(jsonPath("$.data.items[0].name").value("싼곳"))
				.andExpect(jsonPath("$.data.items[0].kind").value("PARKING"))
				.andExpect(jsonPath("$.data.items[0].id").isNotEmpty())
				.andExpect(jsonPath("$.data.items[0].address").value("부산광역시 싼곳"))
				.andExpect(jsonPath("$.data.items[0].distanceM").value(200))
				.andExpect(jsonPath("$.data.items[0].detail.operator").value("PRIVATE"))
				.andExpect(jsonPath("$.data.items[0].detail.estimatedWon").value(1200))
				.andExpect(jsonPath("$.data.items[0].detail.fee.baseWon").value(300))
				.andExpect(jsonPath("$.data.items[0].detail.openNow").value(true))
				.andExpect(jsonPath("$.data.items[0].detail.todayHours.open").value("08:00"))
				.andExpect(jsonPath("$.data.items[0].detail.todayHours.close").value("20:00"))
				.andExpect(jsonPath("$.data.items[0].detail.closesAt").value("20:00"))
				.andExpect(jsonPath("$.data.items[0].detail.allDay").value(false))
				// 모르는 곳: 칸은 있고 값이 null — «없다»와 «모른다»를 화면이 구분해야 한다
				.andExpect(jsonPath("$.data.items[1].name").value("요금모름"))
				.andExpect(jsonPath("$.data.items[1].detail", hasKey("estimatedWon")))
				.andExpect(jsonPath("$.data.items[1].detail.estimatedWon").value(nullValue()))
				.andExpect(jsonPath("$.data.items[1].detail", hasKey("fee")))
				.andExpect(jsonPath("$.data.items[1].detail.fee").value(nullValue()))
				.andExpect(jsonPath("$.data.items[1].detail", hasKey("openNow")))
				.andExpect(jsonPath("$.data.items[1].detail.openNow").value(nullValue()))
				.andExpect(jsonPath("$.data.items[1].detail.todayHours").value(nullValue()))
				.andExpect(jsonPath("$.data.items[1].detail.note").value("2번 출구 · 부산시설공단 · T.860-7713"));
	}

	@Test
	@DisplayName("약국 응답: hoursAreReported 가 늘 true 이고 오늘 시간·일요일/공휴일/심야 표시가 풀려 실린다")
	void pharmacyJson() throws Exception {
		Map<String, Window> days = new LinkedHashMap<>();
		days.put("Wednesday", new Window(9 * 60, 25 * 60));
		days.put("Sunday", new Window(10 * 60, 20 * 60));
		returns(FacilityKind.PHARMACY, facility(FacilityKind.PHARMACY, "가온약국", 150,
				mapOf("tel", "051-241-0123", "directions", "1호선 토성역 8번출구", "note", "점심시간 13:00-13:30", "hours",
						WeeklyHours.of(days).toDetail())));

		this.mockMvc.perform(get("/api/v1/facilities/nearby").param("kind", "PHARMACY").param("lat", "35.1")
				.param("lng", "129.0")).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.stayMinutes").value(nullValue()))
				.andExpect(jsonPath("$.data.items[0].detail.hoursAreReported").value(true))
				.andExpect(jsonPath("$.data.items[0].detail.openNow").value(true))
				.andExpect(jsonPath("$.data.items[0].detail.closesAt").value("01:00"))
				.andExpect(jsonPath("$.data.items[0].detail.todayHours.open").value("09:00"))
				.andExpect(jsonPath("$.data.items[0].detail.opensSunday").value(true))
				.andExpect(jsonPath("$.data.items[0].detail.opensHoliday").value(false))
				.andExpect(jsonPath("$.data.items[0].detail.lateNight").value(true))
				.andExpect(jsonPath("$.data.items[0].detail.tel").value("051-241-0123"))
				.andExpect(jsonPath("$.data.items[0].detail.directions").value("1호선 토성역 8번출구"))
				.andExpect(jsonPath("$.data.items[0].detail.note").value("점심시간 13:00-13:30"));
	}

	@Test
	@DisplayName("화장실·물품보관함 응답과 쿼리 필터(accessible·diaper) — 문자열 true 가 불리언으로 닿는다")
	void toiletAndLockerJson() throws Exception {
		returns(FacilityKind.TOILET,
				facility(FacilityKind.TOILET, "둘다", 100, mapOf("type", "OPEN", "openHours", "09:00~18:00", "alwaysOpen", false,
						"accessible", true, "diaper", true, "diaperPlace", "WOMEN", "emergencyBell", true, "cctv", false,
						"tel", null)),
				facility(FacilityKind.TOILET, "아무것도없음", 50, mapOf("type", "SIMPLE", "accessible", false, "diaper", false)));

		this.mockMvc.perform(get("/api/v1/facilities/nearby").param("kind", "toilet").param("lat", "35.1")
				.param("lng", "129.0").param("accessible", "true").param("diaper", "true")).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.kind").value("TOILET"))
				.andExpect(jsonPath("$.data.total").value(1))
				.andExpect(jsonPath("$.data.items.length()").value(1))
				.andExpect(jsonPath("$.data.items[0].detail.type").value("OPEN"))
				.andExpect(jsonPath("$.data.items[0].detail.openHours").value("09:00~18:00"))
				.andExpect(jsonPath("$.data.items[0].detail.accessible").value(true))
				.andExpect(jsonPath("$.data.items[0].detail.diaperPlace").value("WOMEN"))
				.andExpect(jsonPath("$.data.items[0].detail.emergencyBell").value(true));

		returns(FacilityKind.LOCKER, facility(FacilityKind.LOCKER, "하단역", 80, mapOf("station", "하단", "stationEn", "Hadan",
				"line", "1", "location", "(B1) 1번 엘리베이터 옆", "small", 10, "medium", 10, "large", 1, "xlarge", 7, "feeText",
				"소: 2,000원(3시간당)", "operator", "위드락커")));
		this.mockMvc.perform(get("/api/v1/facilities/nearby").param("kind", "LOCKER").param("lat", "35.1")
				.param("lng", "129.0")).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].detail.station").value("하단"))
				.andExpect(jsonPath("$.data.items[0].detail.stationEn").value("Hadan"))
				.andExpect(jsonPath("$.data.items[0].detail.small").value(10))
				.andExpect(jsonPath("$.data.items[0].detail.xlarge").value(7))
				.andExpect(jsonPath("$.data.items[0].detail.feeText").value("소: 2,000원(3시간당)"));
	}

	@Test
	@DisplayName("서비스에는 쿼리 값이 그대로 닿는다 — 반경·개수·체류 시간을 문자열에서 숫자로")
	void parametersReachTheService() throws Exception {
		returns(FacilityKind.PARKING);

		this.mockMvc.perform(get("/api/v1/facilities/nearby").param("kind", "PARKING").param("lat", "35.1")
				.param("lng", "129.0").param("radiusM", "1500").param("limit", "5").param("stayMinutes", "240"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.radiusM").value(1500))
				.andExpect(jsonPath("$.data.limit").value(5)).andExpect(jsonPath("$.data.stayMinutes").value(240))
				.andExpect(jsonPath("$.data.items.length()").value(0));
		verify(this.repository).findInBox(eq(FacilityKind.PARKING), anyDouble(), anyDouble(), anyDouble(), anyDouble(),
				any(Limit.class));
	}

	@Test
	@DisplayName("🔴 잘못된 요청은 400 INVALID_REQUEST 이고 어느 칸이 문제인지 알린다 — 필수 칸 누락·숫자 아님·범위 밖·모르는 종류")
	void badRequestsAre400() throws Exception {
		this.mockMvc.perform(get("/api/v1/facilities/nearby").param("lat", "35.1").param("lng", "129.0"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
				.andExpect(jsonPath("$.error.fields[0]").value("kind"));
		this.mockMvc.perform(get("/api/v1/facilities/nearby").param("kind", "PARKING").param("lng", "129.0"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields[0]").value("lat"));
		this.mockMvc.perform(get("/api/v1/facilities/nearby").param("kind", "PARKING").param("lat", "abc")
				.param("lng", "129.0")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
				.andExpect(jsonPath("$.error.fields[0]").value("lat"));
		this.mockMvc.perform(get("/api/v1/facilities/nearby").param("kind", "BOGUS").param("lat", "35.1")
				.param("lng", "129.0")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fields[0]").value("kind"));
		this.mockMvc.perform(get("/api/v1/facilities/nearby").param("kind", "PARKING").param("lat", "35.1")
				.param("lng", "129.0").param("radiusM", "2001")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fields[0]").value("radiusM"));
		this.mockMvc.perform(get("/api/v1/facilities/nearby").param("kind", "PARKING").param("lat", "35.1")
				.param("lng", "129.0").param("limit", "0")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fields[0]").value("limit"));
		this.mockMvc.perform(get("/api/v1/facilities/nearby").param("kind", "PARKING").param("lat", "35.1")
				.param("lng", "129.0").param("stayMinutes", "1441")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fields[0]").value("stayMinutes"));
		this.mockMvc.perform(get("/api/v1/facilities/nearby").param("kind", "TOILET").param("lat", "35.1")
				.param("lng", "129.0").param("accessible", "maybe")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fields[0]").value("accessible"));
	}
}
