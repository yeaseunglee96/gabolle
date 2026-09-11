package com.gabolle.backend.tools.adapter;

// 🔴 어댑터와 같은 패키지에 둔다 — 시간 제한 공장을 갈아 끼우는 생성자가 패키지 안에서만 보인다.

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.gabolle.backend.tools.application.TranslationVendorException;
import com.gabolle.backend.tools.config.TranslateProperties;
import com.gabolle.backend.tools.domain.TranslationDirection;

import tools.jackson.databind.ObjectMapper;

/**
 * {@link TranslationVendorAdapter} 검증 — S15P21E201-343.
 *
 * <p>{@code KakaoMobilityRouteAdapterTest} 와 같은 방식으로 {@code MockRestServiceServer} 를
 * 쓴다. 진짜 네트워크를 부르지 않는다.
 */
class TranslationVendorAdapterTest {

	// 🔴 이 문자열이 로그에 절대 나오면 안 된다 — 아래 no-log 검사가 정확히 이 값을 찾는다.
	private static final String SECRET_SOURCE_TEXT = "이 문장은 절대로 로그에 남으면 안 된다";

	private Logger logbackLogger;
	private ListAppender<ILoggingEvent> appender;

	@BeforeEach
	void setUp() {
		this.logbackLogger = (Logger) LoggerFactory.getLogger(TranslationVendorAdapter.class);
		this.appender = new ListAppender<>();
		this.appender.start();
		this.logbackLogger.addAppender(this.appender);
	}

	@AfterEach
	void tearDown() {
		this.logbackLogger.detachAppender(this.appender);
	}

	private TranslationVendorAdapter newAdapter(RestClient.Builder builder, String apiKey, String baseUrl) {
		TranslateProperties properties = new TranslateProperties();
		properties.setVendorApiKey(apiKey);
		properties.setVendorBaseUrl(baseUrl);
		// 🔴 네 번째 인자가 null 이다 — builder 에 꽂힌 가짜 요청 공장을 덮어쓰지 않는다.
		return new TranslationVendorAdapter(builder, new ObjectMapper(), properties, null);
	}

	@Test
	@DisplayName("정상 응답이면 번역 문장을 그대로 돌려준다")
	void mapsSuccessResponse() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		TranslationVendorAdapter adapter = newAdapter(builder, "vendor-key", "https://vendor.example/translate");

		server.expect(requestTo("https://vendor.example/translate"))
				.andExpect(method(HttpMethod.POST))
				.andRespond(withSuccess("{\"translatedText\":\"hello\"}", MediaType.APPLICATION_JSON));

		String result = adapter.translate("안녕", TranslationDirection.KO_TO_EN);

		assertThat(result).isEqualTo("hello");
		server.verify();
	}

	@Test
	@DisplayName("키나 엔드포인트가 비어 있으면 호출조차 안 하고 명확한 실패를 던진다")
	void blankConfigurationFailsClearlyWithoutCalling() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		TranslationVendorAdapter adapter = newAdapter(builder, "", "");

		// 기대를 하나도 걸지 않는다 — 그래도 요청이 나가지 않아야 통과한다.
		assertThatThrownBy(() -> adapter.translate("안녕", TranslationDirection.KO_TO_EN))
				.isInstanceOf(TranslationVendorException.class)
				.satisfies(exception -> {
					TranslationVendorException vendorException = (TranslationVendorException) exception;
					assertThat(vendorException.getCode()).isEqualTo("TRANSLATE_VENDOR_NOT_CONFIGURED");
					assertThat(vendorException.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
				});
		server.verify();
	}

	@Test
	@DisplayName("🔴 호출이 실패하면(500) 미리 정해 둔 문장 대신 명확한 실패를 던진다")
	void serverErrorThrowsInsteadOfFakingSuccess() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		TranslationVendorAdapter adapter = newAdapter(builder, "vendor-key", "https://vendor.example/translate");

		server.expect(requestTo("https://vendor.example/translate"))
				.andRespond(withServerError());

		assertThatThrownBy(() -> adapter.translate(SECRET_SOURCE_TEXT, TranslationDirection.KO_TO_EN))
				.isInstanceOf(TranslationVendorException.class)
				.satisfies(exception -> {
					TranslationVendorException vendorException = (TranslationVendorException) exception;
					assertThat(vendorException.getCode()).isEqualTo("TRANSLATE_VENDOR_UNAVAILABLE");
					assertThat(vendorException.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
				});
		server.verify();
	}

	@Test
	@DisplayName("🔴 실패 로그에 원문이 남지 않는다")
	void failureLogDoesNotContainSourceText() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		TranslationVendorAdapter adapter = newAdapter(builder, "vendor-key", "https://vendor.example/translate");

		server.expect(requestTo("https://vendor.example/translate"))
				.andRespond(withServerError());

		assertThatThrownBy(() -> adapter.translate(SECRET_SOURCE_TEXT, TranslationDirection.KO_TO_EN))
				.isInstanceOf(TranslationVendorException.class);

		assertThat(this.appender.list).isNotEmpty();
		for (ILoggingEvent event : this.appender.list) {
			assertThat(event.getFormattedMessage()).doesNotContain(SECRET_SOURCE_TEXT);
			assertThat(event.getLevel()).isIn(Level.WARN, Level.INFO, Level.ERROR, Level.DEBUG);
		}
	}

	@Test
	@DisplayName("업체가 빈 결과를 주면 명확한 실패다")
	void blankTranslatedTextFailsClearly() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		TranslationVendorAdapter adapter = newAdapter(builder, "vendor-key", "https://vendor.example/translate");

		server.expect(requestTo("https://vendor.example/translate"))
				.andRespond(withSuccess("{\"translatedText\":\"\"}", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> adapter.translate("안녕", TranslationDirection.KO_TO_EN))
				.isInstanceOf(TranslationVendorException.class);
	}
}
