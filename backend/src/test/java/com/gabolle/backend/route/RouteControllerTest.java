package com.gabolle.backend.route;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gabolle.backend.route.adapter.StraightLineRouteEstimator;
import com.gabolle.backend.route.application.RouteCache;
import com.gabolle.backend.route.application.RouteProviderPort;
import com.gabolle.backend.route.application.RouteQueryService;
import com.gabolle.backend.route.config.RouteProperties;
import com.gabolle.backend.route.domain.RouteLeg;
import com.gabolle.backend.route.domain.RouteQuery;
import com.gabolle.backend.route.domain.TravelMode;
import com.gabolle.backend.route.presentation.RouteController;
import com.gabolle.backend.route.presentation.RouteExceptionHandler;

/**
 * {@code GET /api/v1/routes/directions} 의 HTTP 경계 — 경로가 붙어 있는지, 응답 칸 이름이
 * 프론트가 볼 그대로인지, 잘못된 요청이 400 으로 번역되는지를 잰다. 칸 이름을 바꾸면 여기가
 * 빨개진다.
 */
class RouteControllerTest {

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		RouteProperties properties = new RouteProperties();
		RouteQueryService service = new RouteQueryService(
				List.of(new FixedCarProvider(), new FixedWalkProvider()),
				new StraightLineRouteEstimator(properties),
				new RouteCache(properties, Clock.fixed(Instant.parse("2026-09-08T00:00:00Z"), ZoneOffset.UTC)));

		this.mockMvc = MockMvcBuilders.standaloneSetup(new RouteController(service))
				.setControllerAdvice(new RouteExceptionHandler())
				.build();
	}

	private static Authentication asUser() {
		return new TestingAuthenticationToken(UUID.randomUUID().toString(), null);
	}

	@Test
	@DisplayName("자차 경로를 부르면 거리·소요시간·요금·경로 좌표·단계별 안내가 온다")
	void carRouteCarriesEveryFieldTheScreenNeeds() throws Exception {
		this.mockMvc.perform(get("/api/v1/routes/directions")
						.param("originLat", "35.1587").param("originLng", "129.1604")
						.param("destLat", "35.1796").param("destLng", "129.0756")
						.param("mode", "CAR")
						.principal(asUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.mode").value("CAR"))
				.andExpect(jsonPath("$.data.distanceM").value(11132))
				.andExpect(jsonPath("$.data.durationMin").value(44))
				.andExpect(jsonPath("$.data.taxiFareKrw").value(15700))
				.andExpect(jsonPath("$.data.tollFareKrw").value(0))
				.andExpect(jsonPath("$.data.estimated").value(false))
				.andExpect(jsonPath("$.data.provider").value("KAKAO_MOBILITY"))
				// 경로 좌표는 [경도, 위도] 순서다. 뒤집히면 지도에 엉뚱한 곳이 그려진다.
				.andExpect(jsonPath("$.data.path[0][0]").value(129.1604))
				.andExpect(jsonPath("$.data.path[0][1]").value(35.1587))
				.andExpect(jsonPath("$.data.steps[0].guidance").value("송정 방면으로 우회전"));
	}

	@Test
	@DisplayName("🔴 대중교통은 추정으로 오고 그 사실이 응답에 실린다 — 이유까지 함께 온다")
	void transitComesBackAsAnEstimateWithAReason() throws Exception {
		this.mockMvc.perform(get("/api/v1/routes/directions")
						.param("originLat", "35.1587").param("originLng", "129.1604")
						.param("destLat", "35.1796").param("destLng", "129.0756")
						.param("mode", "TRANSIT")
						.principal(asUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.estimated").value(true))
				.andExpect(jsonPath("$.data.estimateReason").isNotEmpty())
				.andExpect(jsonPath("$.data.provider").value("STRAIGHT_LINE"))
				.andExpect(jsonPath("$.data.taxiFareKrw").doesNotExist())
				.andExpect(jsonPath("$.data.steps").isArray())
				// 경사 조각은 우리 보행 그래프가 찾은 걷기에만 있다 — 추정에는 지어내지 않고 빈 목록이다.
				.andExpect(jsonPath("$.data.pieces").isArray())
				.andExpect(jsonPath("$.data.pieces").isEmpty());
	}

	@Test
	@DisplayName("🔴 걷기는 실제 길 모양과 경사 조각(pieces)이 온다 — 칸 이름이 프론트와의 계약이다 (S15P21E201-1630)")
	void walkCarriesPathAndSlopePieces() throws Exception {
		this.mockMvc.perform(get("/api/v1/routes/directions")
						.param("originLat", "35.163672").param("originLng", "129.158908")
						.param("destLat", "35.158523").param("destLng", "129.159855")
						.param("mode", "WALK")
						.principal(asUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.mode").value("WALK"))
				.andExpect(jsonPath("$.data.estimated").value(false))
				.andExpect(jsonPath("$.data.provider").value("OSM_WALK_GRAPH"))
				.andExpect(jsonPath("$.data.path.length()").value(4))
				.andExpect(jsonPath("$.data.pieces[0].from").value(0))
				.andExpect(jsonPath("$.data.pieces[0].to").value(1))
				.andExpect(jsonPath("$.data.pieces[0].slopePercent").doesNotExist())
				.andExpect(jsonPath("$.data.pieces[0].stairs").value(false))
				.andExpect(jsonPath("$.data.pieces[1].slopePercent").value(9.5))
				.andExpect(jsonPath("$.data.pieces[2].stairs").value(true));
	}

	@Test
	@DisplayName("이동수단을 안 주면 자차로 본다")
	void modeDefaultsToCar() throws Exception {
		this.mockMvc.perform(get("/api/v1/routes/directions")
						.param("originLat", "35.1587").param("originLng", "129.1604")
						.param("destLat", "35.1796").param("destLng", "129.0756")
						.principal(asUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.mode").value("CAR"));
	}

	@Test
	@DisplayName("모르는 이동수단은 400 이고, 무엇이 잘못됐는지 응답에 적힌다")
	void unknownModeIsRejected() throws Exception {
		this.mockMvc.perform(get("/api/v1/routes/directions")
						.param("originLat", "35.1587").param("originLng", "129.1604")
						.param("destLat", "35.1796").param("destLng", "129.0756")
						.param("mode", "HELICOPTER")
						.principal(asUser()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("ROUTE_INVALID_REQUEST"))
				.andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("CAR")));
	}

	@Test
	@DisplayName("범위를 벗어난 좌표는 400 이다 — 바깥 업체에 그대로 넘기지 않는다")
	void outOfRangeCoordinateIsRejected() throws Exception {
		this.mockMvc.perform(get("/api/v1/routes/directions")
						.param("originLat", "935.1587").param("originLng", "129.1604")
						.param("destLat", "35.1796").param("destLng", "129.0756")
						.principal(asUser()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("ROUTE_INVALID_REQUEST"));
	}

	@Test
	@DisplayName("좌표 자리에 숫자가 아닌 것이 오면 400 이고 어느 칸인지 알려준다")
	void nonNumericCoordinateNamesTheField() throws Exception {
		this.mockMvc.perform(get("/api/v1/routes/directions")
						.param("originLat", "여기").param("originLng", "129.1604")
						.param("destLat", "35.1796").param("destLng", "129.0756")
						.principal(asUser()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fields[0]").value("originLat"));
	}

	@Test
	@DisplayName("필수 좌표가 빠지면 400 이고 빠진 칸 이름이 온다")
	void missingParameterNamesTheField() throws Exception {
		this.mockMvc.perform(get("/api/v1/routes/directions")
						.param("originLat", "35.1587").param("originLng", "129.1604")
						.param("destLat", "35.1796")
						.principal(asUser()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fields[0]").value("destLng"));
	}

	/** 자차만 답하는 가짜 업체 — 실제 카카오 응답에서 뽑은 값을 그대로 쓴다. */
	/** 우리 보행 그래프가 찾은 걷기 — 길 밖 토막(모름) · 가파른 길 9.5% · 계단. */
	private static final class FixedWalkProvider implements RouteProviderPort {

		@Override
		public boolean supports(TravelMode mode) {
			return mode == TravelMode.WALK;
		}

		@Override
		public Optional<RouteLeg> find(RouteQuery query) {
			return Optional.of(new RouteLeg(TravelMode.WALK, 707, 11, null, null, null, false, null,
					RouteLeg.PROVIDER_WALK_GRAPH,
					List.of(new double[] { 129.158908, 35.163672 }, new double[] { 129.1589, 35.1635 },
							new double[] { 129.1595, 35.1600 }, new double[] { 129.159855, 35.158523 }),
					List.of(), null,
					List.of(new RouteLeg.Piece(0, 1, null, false), new RouteLeg.Piece(1, 2, 9.5, false),
							new RouteLeg.Piece(2, 3, null, true))));
		}

		@Override
		public String providerName() {
			return RouteLeg.PROVIDER_WALK_GRAPH;
		}
	}

	private static final class FixedCarProvider implements RouteProviderPort {

		@Override
		public boolean supports(TravelMode mode) {
			return mode == TravelMode.CAR;
		}

		@Override
		public Optional<RouteLeg> find(RouteQuery query) {
			return Optional.of(new RouteLeg(TravelMode.CAR, 11132, 44, 15700, 0, null, false, null,
					RouteLeg.PROVIDER_KAKAO_MOBILITY,
					List.of(new double[] { 129.1604, 35.1587 }, new double[] { 129.0756, 35.1796 }),
					List.of(new RouteLeg.Step("해운대해수욕장삼거리", "송정 방면으로 우회전", 38, 0))));
		}

		@Override
		public String providerName() {
			return RouteLeg.PROVIDER_KAKAO_MOBILITY;
		}
	}
}
