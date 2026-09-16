package com.gabolle.backend.transit.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gabolle.backend.transit.application.TransitService;
import com.gabolle.backend.transit.application.TransitVendorException;
import com.gabolle.backend.transit.application.TransitVendorPort;
import com.gabolle.backend.transit.config.TransitProperties;

import tools.jackson.databind.ObjectMapper;

/**
 * {@code GET /api/v1/transit/nearby-bus-arrivals}의 HTTP 경계 — S15P21E201-988.
 *
 * <p>{@code WeatherControllerTest}와 같은 방식으로 컨트롤러+예외 처리기만 세워 HTTP 계약을
 * 잰다.
 */
class TransitControllerTest {

	private static final String STOPS_JSON = """
			{"response":{"header":{"resultCode":"00"},"body":{"items":{"item":
			{"nodeid":"DJB8001793","nodenm":"해운대해수욕장","citycode":"25","gpslati":35.1587,"gpslong":129.1604}
			}}}}""";

	private static final String ARRIVALS_JSON = """
			{"response":{"header":{"resultCode":"00"},"body":{"items":{"item":
			{"routeno":"139","arrtime":420,"arrprevstationcnt":3,"vehicletp":"저상버스"}
			}}}}""";

	private MockMvc mockMvc;
	private StubVendor vendor;

	@BeforeEach
	void setUp() {
		this.vendor = new StubVendor();
		TransitProperties properties = new TransitProperties();
		TransitService service = new TransitService(this.vendor, properties, new ObjectMapper());

		this.mockMvc = MockMvcBuilders.standaloneSetup(new TransitController(service))
				.setControllerAdvice(new TransitExceptionHandler())
				.build();
	}

	private static Authentication asUser() {
		return new TestingAuthenticationToken(UUID.randomUUID().toString(), null);
	}

	@Test
	@DisplayName("좌표로 부르면 근처 정류소와 도착 예정 버스가 돌아온다")
	void nearbyBusArrivalsSucceeds() throws Exception {
		this.mockMvc.perform(get("/api/v1/transit/nearby-bus-arrivals")
						.param("lat", "35.1587")
						.param("lng", "129.1604")
						.principal(asUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.stops[0].nodeId").value("DJB8001793"))
				.andExpect(jsonPath("$.data.stops[0].nodeName").value("해운대해수욕장"))
				.andExpect(jsonPath("$.data.stops[0].arrivals[0].routeNo").value("139"))
				.andExpect(jsonPath("$.data.stops[0].arrivals[0].arrivalSeconds").value(420));
	}

	@Test
	@DisplayName("🔴 벤더 호출이 실패하면 502 이고 응답에 실패가 분명히 담긴다 — 200 으로 숨기지 않는다")
	void vendorFailureIsNotHiddenAs200() throws Exception {
		this.vendor.shouldFail = true;

		this.mockMvc.perform(get("/api/v1/transit/nearby-bus-arrivals")
						.param("lat", "35.1587")
						.param("lng", "129.1604")
						.principal(asUser()))
				.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.error.code").value("TRANSIT_VENDOR_UNAVAILABLE"))
				.andExpect(jsonPath("$.data").doesNotExist());
	}

	@Test
	@DisplayName("필수 값이 빠지면 400 이다")
	void missingParameterIsRejected() throws Exception {
		this.mockMvc.perform(get("/api/v1/transit/nearby-bus-arrivals")
						.param("lat", "35.1587")
						.principal(asUser()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("TRANSIT_INVALID_REQUEST"));
	}

	@Test
	@DisplayName("숫자 자리에 그 형식이 아닌 것이 오면 400 이다")
	void malformedCoordinateIsRejected() throws Exception {
		this.mockMvc.perform(get("/api/v1/transit/nearby-bus-arrivals")
						.param("lat", "abc")
						.param("lng", "129.1604")
						.principal(asUser()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("TRANSIT_INVALID_REQUEST"));
	}

	private static final class StubVendor implements TransitVendorPort {

		boolean shouldFail = false;

		@Override
		public String fetchNearbyStopsJson(double lat, double lng) {
			if (this.shouldFail) {
				throw new TransitVendorException("TRANSIT_VENDOR_UNAVAILABLE", "대중교통 호출에 실패했습니다.",
						HttpStatus.BAD_GATEWAY);
			}
			return STOPS_JSON;
		}

		@Override
		public String fetchArrivalsJson(String cityCode, String nodeId) {
			return ARRIVALS_JSON;
		}

		@Override
		public String providerName() {
			return "STUB_VENDOR";
		}
	}
}
