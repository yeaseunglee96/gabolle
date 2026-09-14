package com.gabolle.backend.weather.adapter;

// 🔴 어댑터와 같은 패키지에 둔다 — 시간 제한 공장을 갈아 끼우는 생성자가 패키지 안에서만 보인다.

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.web.client.RestClient;

import com.gabolle.backend.weather.application.WeatherVendorException;
import com.gabolle.backend.weather.config.WeatherProperties;
import com.gabolle.backend.weather.domain.KmaBaseTime;

/**
 * {@link KmaWeatherVendorAdapter} 검증 — S15P21E201-366.
 *
 * <p>{@code TranslationVendorAdapterTest}·{@code KakaoMobilityRouteAdapterTest} 와 같은
 * 방식으로 {@code MockRestServiceServer} 를 쓴다. 진짜 네트워크를 부르지 않는다.
 */
class KmaWeatherVendorAdapterTest {

	private static final KmaBaseTime BASE_TIME = new KmaBaseTime(LocalDate.of(2026, 9, 10), LocalTime.of(8, 0));

	private KmaWeatherVendorAdapter newAdapter(RestClient.Builder builder, String serviceKey) {
		WeatherProperties properties = new WeatherProperties();
		properties.setKmaServiceKey(serviceKey);
		// 🔴 세 번째 인자가 null 이다 — builder 에 꽂힌 가짜 요청 공장을 덮어쓰지 않는다.
		return new KmaWeatherVendorAdapter(builder, properties, null);
	}

	@Test
	@DisplayName("정상 응답이면 원문 그대로 돌려준다")
	void returnsRawBodyOnSuccess() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		KmaWeatherVendorAdapter adapter = newAdapter(builder, "test-service-key");

		String body = "{\"response\":{\"header\":{\"resultCode\":\"00\"}}}";
		server.expect(ExpectedCount.once(), MockRestRequestMatchers.anything())
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

		String result = adapter.fetchForecastJson(60, 127, BASE_TIME);

		assertThat(result).isEqualTo(body);
		server.verify();
	}

	@Test
	@DisplayName("서비스 키가 비어 있으면 호출조차 안 하고 명확한 실패를 던진다")
	void blankServiceKeyFailsClearlyWithoutCalling() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		KmaWeatherVendorAdapter adapter = newAdapter(builder, "");

		// 기대를 하나도 걸지 않는다 — 그래도 요청이 나가지 않아야 통과한다.
		assertThatThrownBy(() -> adapter.fetchForecastJson(60, 127, BASE_TIME))
				.isInstanceOf(WeatherVendorException.class)
				.satisfies(exception -> {
					WeatherVendorException vendorException = (WeatherVendorException) exception;
					assertThat(vendorException.getCode()).isEqualTo("WEATHER_VENDOR_NOT_CONFIGURED");
					assertThat(vendorException.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
				});
		server.verify();
	}

	@Test
	@DisplayName("🔴 호출이 실패하면(500) 미리 정해 둔 값 대신 명확한 실패를 던진다")
	void serverErrorThrowsInsteadOfFakingSuccess() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		KmaWeatherVendorAdapter adapter = newAdapter(builder, "test-service-key");

		server.expect(ExpectedCount.once(), MockRestRequestMatchers.anything())
				.andRespond(withServerError());

		assertThatThrownBy(() -> adapter.fetchForecastJson(60, 127, BASE_TIME))
				.isInstanceOf(WeatherVendorException.class)
				.satisfies(exception -> {
					WeatherVendorException vendorException = (WeatherVendorException) exception;
					assertThat(vendorException.getCode()).isEqualTo("WEATHER_VENDOR_UNAVAILABLE");
					assertThat(vendorException.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
				});
		server.verify();
	}
}
