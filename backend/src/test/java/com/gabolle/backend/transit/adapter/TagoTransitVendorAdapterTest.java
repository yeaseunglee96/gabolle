package com.gabolle.backend.transit.adapter;

// 어댑터와 같은 패키지에 둔다 — 시간 제한 공장을 갈아 끼우는 생성자가 패키지 안에서만 보인다.

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.web.client.RestClient;

import com.gabolle.backend.transit.application.TransitVendorException;
import com.gabolle.backend.transit.config.TransitProperties;

class TagoTransitVendorAdapterTest {

	private TagoTransitVendorAdapter newAdapter(RestClient.Builder builder, String serviceKey) {
		TransitProperties properties = new TransitProperties();
		properties.setServiceKey(serviceKey);
		// 세 번째 인자가 null 이다 — builder 에 꽂힌 가짜 요청 공장을 덮어쓰지 않는다.
		return new TagoTransitVendorAdapter(builder, properties, null);
	}

	@Test
	@DisplayName("근처 정류소 조회 — 정상 응답이면 원문 그대로 돌려준다")
	void returnsRawBodyOnSuccessForNearbyStops() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		TagoTransitVendorAdapter adapter = newAdapter(builder, "test-service-key");

		String body = "{\"response\":{\"header\":{\"resultCode\":\"00\"}}}";
		server.expect(ExpectedCount.once(), MockRestRequestMatchers.anything())
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

		String result = adapter.fetchNearbyStopsJson(35.1796, 129.0756);

		assertThat(result).isEqualTo(body);
		server.verify();
	}

	@Test
	@DisplayName("도착정보 조회 — 정상 응답이면 원문 그대로 돌려준다")
	void returnsRawBodyOnSuccessForArrivals() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		TagoTransitVendorAdapter adapter = newAdapter(builder, "test-service-key");

		String body = "{\"response\":{\"header\":{\"resultCode\":\"00\"}}}";
		server.expect(ExpectedCount.once(), MockRestRequestMatchers.anything())
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

		String result = adapter.fetchArrivalsJson("25", "DJB8001793");

		assertThat(result).isEqualTo(body);
		server.verify();
	}

	@Test
	@DisplayName("서비스 키가 비어 있으면 호출조차 안 하고 명확한 실패를 던진다")
	void blankServiceKeyFailsClearlyWithoutCalling() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		TagoTransitVendorAdapter adapter = newAdapter(builder, "");

		// 기대를 하나도 걸지 않는다 — 그래도 요청이 나가지 않아야 통과한다.
		assertThatThrownBy(() -> adapter.fetchNearbyStopsJson(35.1796, 129.0756))
				.isInstanceOf(TransitVendorException.class)
				.satisfies(exception -> {
					TransitVendorException vendorException = (TransitVendorException) exception;
					assertThat(vendorException.getCode()).isEqualTo("TRANSIT_VENDOR_NOT_CONFIGURED");
					assertThat(vendorException.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
				});
		server.verify();
	}

	@Test
	@DisplayName("🔴 호출이 실패하면(500) 미리 정해 둔 값 대신 명확한 실패를 던진다")
	void serverErrorThrowsInsteadOfFakingSuccess() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		TagoTransitVendorAdapter adapter = newAdapter(builder, "test-service-key");

		server.expect(ExpectedCount.once(), MockRestRequestMatchers.anything())
				.andRespond(withServerError());

		assertThatThrownBy(() -> adapter.fetchNearbyStopsJson(35.1796, 129.0756))
				.isInstanceOf(TransitVendorException.class)
				.satisfies(exception -> {
					TransitVendorException vendorException = (TransitVendorException) exception;
					assertThat(vendorException.getCode()).isEqualTo("TRANSIT_VENDOR_UNAVAILABLE");
					assertThat(vendorException.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
				});
		server.verify();
	}

	@Test
	@DisplayName("🔴 업체가 우리 키를 거절하면(403) 「잠시 후 다시 시도」가 아니라 「설정 안 됨」이다")
	void rejectedServiceKeyIsNotConfiguredNotUnavailable() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		TagoTransitVendorAdapter adapter = newAdapter(builder, "not-registered-key");

		// 공공데이터포털이 실제로 주는 모양
		String body = "{\"OpenAPI_ServiceResponse\":{\"cmmMsgHeader\":{"
				+ "\"errMsg\":\"SERVICE_KEY_IS_NOT_REGISTERED_ERROR\",\"returnReasonCode\":\"30\"}}}";
		server.expect(ExpectedCount.once(), MockRestRequestMatchers.anything())
				.andRespond(withStatus(HttpStatus.FORBIDDEN).body(body).contentType(MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> adapter.fetchNearbyStopsJson(35.1796, 129.0756))
				.isInstanceOf(TransitVendorException.class)
				.satisfies(exception -> {
					TransitVendorException vendorException = (TransitVendorException) exception;
					// 몇 번을 다시 눌러도 같은 답이 온다 — 「잠시」가 아니다.
					assertThat(vendorException.getCode()).isEqualTo("TRANSIT_VENDOR_NOT_CONFIGURED");
					assertThat(vendorException.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
				});
		server.verify();
	}

	@Test
	@DisplayName("키 문제가 아닌 4xx(404)는 그대로 「잠시 응답하지 않음」이다")
	void otherClientErrorStaysUnavailable() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		TagoTransitVendorAdapter adapter = newAdapter(builder, "test-service-key");

		server.expect(ExpectedCount.once(), MockRestRequestMatchers.anything())
				.andRespond(withStatus(HttpStatus.NOT_FOUND));

		assertThatThrownBy(() -> adapter.fetchNearbyStopsJson(35.1796, 129.0756))
				.isInstanceOf(TransitVendorException.class)
				.satisfies(exception -> assertThat(((TransitVendorException) exception).getCode())
						.isEqualTo("TRANSIT_VENDOR_UNAVAILABLE"));
		server.verify();
	}
}
