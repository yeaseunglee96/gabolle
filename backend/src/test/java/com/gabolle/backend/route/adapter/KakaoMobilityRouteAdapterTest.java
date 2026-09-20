package com.gabolle.backend.route.adapter;

// 이 검사는 어댑터와 **같은 패키지**에 둔다. 시간 제한 공장을 갈아 끼우는 생성자가
// 패키지 안에서만 보이기 때문이다 — 그 자리를 테스트 때문에 public 으로 열지 않는다.

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Optional;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.gabolle.backend.route.config.RouteProperties;
import com.gabolle.backend.route.domain.RouteLeg;
import com.gabolle.backend.route.domain.RouteQuery;
import com.gabolle.backend.route.domain.TravelMode;

import tools.jackson.databind.ObjectMapper;

class KakaoMobilityRouteAdapterTest {

	// 실제 카카오 응답을 줄인 것 — routes[0] 하나, section 하나, road 하나(점 두 개),
	// guide 하나로 줄였다.
	private static final String SUCCESS_RESPONSE = """
			{
			  "routes": [{
			    "result_code": 0,
			    "result_msg": "길찾기 성공",
			    "summary": {
			      "distance": 11132,
			      "duration": 2640,
			      "fare": { "taxi": 15700, "toll": 0 }
			    },
			    "sections": [{
			      "distance": 11132,
			      "duration": 2640,
			      "roads": [
			        { "name": "해운대로", "distance": 87, "duration": 20,
			          "vertexes": [129.1602149653054, 35.15931283877301, 129.1603139909809, 35.159329918011586] }
			      ],
			      "guides": [
			        { "name": "해운대해수욕장삼거리", "x": 129.16048167935284, "y": 35.15954464339784,
			          "distance": 38, "duration": 9, "type": 2,
			          "guidance": "송정 방면으로 우회전", "road_index": 1 }
			      ]
			    }]
			  }]
			}
			""";

	private static final RouteQuery QUERY = new RouteQuery(35.15931283877301, 129.1602149653054,
			35.159329918011586, 129.1603139909809, TravelMode.CAR);

	private KakaoMobilityRouteAdapter newAdapter(RestClient.Builder builder, String apiKey) {
		RouteProperties properties = new RouteProperties();
		properties.setKakaoRestApiKey(apiKey);
		// 네 번째 인자가 null 이다 — builder 에 꽂힌 가짜 요청 공장을 덮어쓰지 않는다.
		// 덮어쓰면 가짜 서버에 요청이 안 닿고 진짜 카카오를 부르러 나간다.
		return new KakaoMobilityRouteAdapter(builder, new ObjectMapper(), properties, null);
	}

	@Test
	@DisplayName("정상 응답을 우리 모양으로 옮긴다")
	void mapsSuccessResponseToOurShape() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		KakaoMobilityRouteAdapter adapter = newAdapter(builder, "kakao-mobility-key");

		server.expect(requestTo(Matchers.containsString("/v1/directions")))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(SUCCESS_RESPONSE, MediaType.APPLICATION_JSON));

		Optional<RouteLeg> result = adapter.find(QUERY);

		assertThat(result).isPresent();
		RouteLeg leg = result.get();
		assertThat(leg.distanceM()).isEqualTo(11132);
		// 2640초 → 44분. 이 어댑터에서 가장 틀리기 쉬운 자리라 값을 못 박는다.
		assertThat(leg.durationMin()).isEqualTo(44);
		assertThat(leg.taxiFareKrw()).isEqualTo(15700);
		assertThat(leg.tollFareKrw()).isEqualTo(0);
		assertThat(leg.transferCount()).isNull();
		assertThat(leg.estimated()).isFalse();
		assertThat(leg.provider()).isEqualTo(RouteLeg.PROVIDER_KAKAO_MOBILITY);
		assertThat(leg.path()).hasSize(2);
		assertThat(leg.steps()).hasSize(1);
		assertThat(leg.steps().get(0).name()).isEqualTo("해운대해수욕장삼거리");
		assertThat(leg.steps().get(0).guidance()).isEqualTo("송정 방면으로 우회전");
		assertThat(leg.steps().get(0).distanceM()).isEqualTo(38);
		server.verify();
	}

	@Test
	@DisplayName("vertexes 가 경도·위도 순서로 짝지어진다")
	void pairsVertexesAsLngThenLat() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		KakaoMobilityRouteAdapter adapter = newAdapter(builder, "kakao-mobility-key");

		server.expect(requestTo(Matchers.containsString("/v1/directions")))
				.andRespond(withSuccess(SUCCESS_RESPONSE, MediaType.APPLICATION_JSON));

		RouteLeg leg = adapter.find(QUERY).orElseThrow();

		double[] firstPoint = leg.path().get(0);
		// 순서를 뒤집으면 경도(129대)와 위도(35대)가 바뀐다 — 이 검사가 그 실수를 잡는다.
		assertThat(firstPoint[0]).isBetween(129.0, 130.0);
		assertThat(firstPoint[1]).isBetween(35.0, 36.0);
	}

	@Test
	@DisplayName("요금 칸이 없으면 null 이다")
	void missingFareFieldsAreNullNotZero() {
		String responseWithoutFare = """
				{
				  "routes": [{
				    "result_code": 0,
				    "summary": { "distance": 1000, "duration": 120 },
				    "sections": [{ "roads": [], "guides": [] }]
				  }]
				}
				""";
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		KakaoMobilityRouteAdapter adapter = newAdapter(builder, "kakao-mobility-key");

		server.expect(requestTo(Matchers.containsString("/v1/directions")))
				.andRespond(withSuccess(responseWithoutFare, MediaType.APPLICATION_JSON));

		RouteLeg leg = adapter.find(QUERY).orElseThrow();

		// asInt() 는 없는 칸에 조용히 0 을 준다 — has() 로 먼저 확인하지 않으면
		// "통행료를 모른다" 가 "통행료가 없다" 로 둔갑한다.
		assertThat(leg.taxiFareKrw()).isNull();
		assertThat(leg.tollFareKrw()).isNull();
	}

	@Test
	@DisplayName("result_code 가 0 이 아니면 빈 값이다")
	void nonZeroResultCodeYieldsEmpty() {
		String failedResponse = """
				{
				  "routes": [{
				    "result_code": 1,
				    "result_msg": "경로를 찾을 수 없습니다",
				    "summary": { "distance": 0, "duration": 0 },
				    "sections": []
				  }]
				}
				""";
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		KakaoMobilityRouteAdapter adapter = newAdapter(builder, "kakao-mobility-key");

		// 카카오는 길을 못 찾아도 HTTP 200 을 준다 — 상태 코드만 보면 거리 0 짜리
		// 가짜 경로가 그대로 나간다.
		server.expect(requestTo(Matchers.containsString("/v1/directions")))
				.andRespond(withSuccess(failedResponse, MediaType.APPLICATION_JSON));

		Optional<RouteLeg> result = adapter.find(QUERY);

		assertThat(result).isEmpty();
	}

	@Test
	@DisplayName("키가 비어 있으면 호출조차 안 하고 빈 값이다")
	void blankApiKeySkipsCallAndReturnsEmpty() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		KakaoMobilityRouteAdapter adapter = newAdapter(builder, "");

		// 기대를 하나도 걸지 않는다 — 그래도 요청이 나가지 않아야 통과한다.
		Optional<RouteLeg> result = adapter.find(QUERY);

		assertThat(result).isEmpty();
		server.verify();
	}

	@Test
	@DisplayName("CAR 가 아닌 이동수단은 빈 값이다")
	void nonCarModeReturnsEmpty() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		KakaoMobilityRouteAdapter adapter = newAdapter(builder, "kakao-mobility-key");
		RouteQuery walkQuery = new RouteQuery(35.15931283877301, 129.1602149653054, 35.159329918011586,
				129.1603139909809, TravelMode.WALK);

		assertThat(adapter.supports(TravelMode.WALK)).isFalse();
		Optional<RouteLeg> result = adapter.find(walkQuery);

		assertThat(result).isEmpty();
		server.verify();
	}

	@Test
	@DisplayName("호출이 실패하면(500) 예외가 아니라 빈 값이다")
	void serverErrorReturnsEmptyInsteadOfThrowing() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		KakaoMobilityRouteAdapter adapter = newAdapter(builder, "kakao-mobility-key");

		server.expect(requestTo(Matchers.containsString("/v1/directions")))
				.andRespond(withServerError());

		// 이게 추정으로 넘어가는 길을 지키는 검사다 — 여기서 예외가 새면 부르는 쪽이
		// 무조건 잡아야 하는 코드가 되고, 그 순간 이 포트의 계약이 깨진다.
		Optional<RouteLeg> result = adapter.find(QUERY);

		assertThat(result).isEmpty();
		server.verify();
	}
}
