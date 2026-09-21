package com.gabolle.backend.exchangerate.adapter;

// 어댑터와 같은 패키지에 둔다 — 시간 제한 공장을 갈아 끼우는 생성자가 패키지 안에서만 보인다.

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.web.client.RestClient;

import com.gabolle.backend.exchangerate.application.ExchangeRateVendorException;
import com.gabolle.backend.exchangerate.config.ExchangeRateProperties;

class KoreaeximExchangeRateVendorAdapterTest {

	private static final LocalDate SEARCH_DATE = LocalDate.of(2026, 9, 16);

	private KoreaeximExchangeRateVendorAdapter newAdapter(RestClient.Builder builder, String authKey) {
		ExchangeRateProperties properties = new ExchangeRateProperties();
		properties.setAuthKey(authKey);
		// 세 번째 인자가 null 이다 — builder 에 꽂힌 가짜 요청 공장을 덮어쓰지 않는다.
		return new KoreaeximExchangeRateVendorAdapter(builder, properties, null);
	}

	@Test
	@DisplayName("정상 응답이면 원문 그대로 돌려준다")
	void returnsRawBodyOnSuccess() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		KoreaeximExchangeRateVendorAdapter adapter = newAdapter(builder, "test-auth-key");

		String body = "[{\"result\":1,\"cur_unit\":\"USD\",\"deal_bas_r\":\"1,378.60\"}]";
		server.expect(ExpectedCount.once(), MockRestRequestMatchers.anything())
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

		String result = adapter.fetchRatesJson(SEARCH_DATE);

		assertThat(result).isEqualTo(body);
		server.verify();
	}

	@Test
	@DisplayName("인증키가 비어 있으면 호출조차 안 하고 명확한 실패를 던진다")
	void blankAuthKeyFailsClearlyWithoutCalling() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		KoreaeximExchangeRateVendorAdapter adapter = newAdapter(builder, "");

		// 기대를 하나도 걸지 않는다 — 그래도 요청이 나가지 않아야 통과한다.
		assertThatThrownBy(() -> adapter.fetchRatesJson(SEARCH_DATE))
				.isInstanceOf(ExchangeRateVendorException.class)
				.satisfies(exception -> {
					ExchangeRateVendorException vendorException = (ExchangeRateVendorException) exception;
					assertThat(vendorException.getCode()).isEqualTo("EXCHANGE_RATE_VENDOR_NOT_CONFIGURED");
					assertThat(vendorException.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
				});
		server.verify();
	}

	@Test
	@DisplayName("🔴 호출이 실패하면(500) 미리 정해 둔 값 대신 명확한 실패를 던진다")
	void serverErrorThrowsInsteadOfFakingSuccess() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		KoreaeximExchangeRateVendorAdapter adapter = newAdapter(builder, "test-auth-key");

		server.expect(ExpectedCount.once(), MockRestRequestMatchers.anything())
				.andRespond(withServerError());

		assertThatThrownBy(() -> adapter.fetchRatesJson(SEARCH_DATE))
				.isInstanceOf(ExchangeRateVendorException.class)
				.satisfies(exception -> {
					ExchangeRateVendorException vendorException = (ExchangeRateVendorException) exception;
					assertThat(vendorException.getCode()).isEqualTo("EXCHANGE_RATE_VENDOR_UNAVAILABLE");
					assertThat(vendorException.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
				});
		server.verify();
	}
}
