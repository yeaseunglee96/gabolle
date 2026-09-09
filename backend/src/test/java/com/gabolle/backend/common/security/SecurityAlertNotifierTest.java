package com.gabolle.backend.common.security;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * S15P21E201-682 — 급증 판정(슬라이딩 윈도)·cooldown·웹훅 설정 없음/실패 시 동작을 본다.
 *
 * <p>🔴 실제 MatterMost 웹훅은 검증할 수 없다(팀에 아직 주소가 없다). 여기서는 JDK 내장
 * {@link HttpServer} 로 가짜 수신 서버를 띄우거나, 티켓이 지시한 대로 {@code http://localhost:1/hook}
 * 같은 명백히 닿지 않는 주소를 써서 "전송 시도" 까지만 검증한다.
 */
class SecurityAlertNotifierTest {

	private HttpServer fakeWebhookServer;

	@AfterEach
	void tearDown() {
		if (this.fakeWebhookServer != null) {
			this.fakeWebhookServer.stop(0);
		}
	}

	@Test
	@DisplayName("윈도 안 건수가 임계치를 넘으면 웹훅으로 한 번 보낸다")
	void sendsAlertOnceThresholdIsCrossed() throws IOException {
		AtomicInteger receivedRequests = new AtomicInteger();
		startFakeWebhookServer(receivedRequests);

		SecurityAlertNotifier notifier = notifierWithThreshold(3, this.fakeWebhookServer);

		notifier.recordAndMaybeAlert(SecurityEvent.AUTH_LOGIN_FAILURE);
		notifier.recordAndMaybeAlert(SecurityEvent.AUTH_LOGIN_FAILURE);
		assertThat(receivedRequests.get()).as("임계치 전에는 아직 안 보낸다").isZero();

		notifier.recordAndMaybeAlert(SecurityEvent.AUTH_LOGIN_FAILURE);
		assertThat(receivedRequests.get()).as("세 번째에 임계치(3)를 넘겨 한 번 보낸다").isEqualTo(1);
	}

	@Test
	@DisplayName("🔴 완료 기준 — cooldown 동안은 같은 종류를 다시 보내지 않는다")
	void doesNotResendTheSameEventDuringCooldown() throws IOException {
		AtomicInteger receivedRequests = new AtomicInteger();
		startFakeWebhookServer(receivedRequests);

		SecurityAlertProperties properties = new SecurityAlertProperties();
		properties.setWebhookUrl(webhookUrl(this.fakeWebhookServer));
		properties.setWindow(Duration.ofMinutes(5));
		properties.setThreshold(1);
		properties.setCooldown(Duration.ofMinutes(10));
		SecurityAlertNotifier notifier = new SecurityAlertNotifier(properties, RestClient.builder(),
				Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

		notifier.recordAndMaybeAlert(SecurityEvent.AUTH_LOGIN_FAILURE);
		notifier.recordAndMaybeAlert(SecurityEvent.AUTH_LOGIN_FAILURE);
		notifier.recordAndMaybeAlert(SecurityEvent.AUTH_LOGIN_FAILURE);

		assertThat(receivedRequests.get()).as("곧바로 다시 넘겨도 cooldown 동안은 한 번만 나간다").isEqualTo(1);
	}

	@Test
	@DisplayName("🔴 완료 기준 — 웹훅 설정이 비어 있으면 로그로만 남고 예외가 나지 않는다")
	void emptyWebhookUrlDoesNotThrow() {
		SecurityAlertProperties properties = new SecurityAlertProperties();
		properties.setThreshold(1);
		// webhookUrl 기본값이 이미 빈 문자열이다 — 명시적으로 다시 비운다.
		properties.setWebhookUrl("");
		SecurityAlertNotifier notifier = new SecurityAlertNotifier(properties, RestClient.builder(), Clock.systemUTC());

		assertThatCode(() -> notifier.recordAndMaybeAlert(SecurityEvent.AUTH_ACCOUNT_LOCKED))
				.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("🔴 가장 위험한 자리 — 웹훅 전송이 실패해도(닿지 않는 주소) 예외가 밖으로 안 나간다")
	void unreachableWebhookDoesNotThrow() {
		SecurityAlertProperties properties = new SecurityAlertProperties();
		properties.setThreshold(1);
		// 🔴 실제 주소처럼 보이는 값을 만들지 않는다 — 명백히 가짜인 예약 포트를 쓴다.
		properties.setWebhookUrl("http://localhost:1/hook");
		SecurityAlertNotifier notifier = new SecurityAlertNotifier(properties, RestClient.builder(), Clock.systemUTC());

		assertThatCode(() -> notifier.recordAndMaybeAlert(SecurityEvent.AUTH_LOGIN_FAILURE))
				.doesNotThrowAnyException();
	}

	private SecurityAlertNotifier notifierWithThreshold(int threshold, HttpServer server) {
		SecurityAlertProperties properties = new SecurityAlertProperties();
		properties.setWebhookUrl(webhookUrl(server));
		properties.setWindow(Duration.ofMinutes(5));
		properties.setThreshold(threshold);
		properties.setCooldown(Duration.ofMinutes(10));
		return new SecurityAlertNotifier(properties, RestClient.builder(),
				Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
	}

	private void startFakeWebhookServer(AtomicInteger receivedRequests) throws IOException {
		this.fakeWebhookServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		this.fakeWebhookServer.createContext("/hook", exchange -> {
			receivedRequests.incrementAndGet();
			byte[] body = "ok".getBytes();
			exchange.sendResponseHeaders(200, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		this.fakeWebhookServer.start();
	}

	private String webhookUrl(HttpServer server) {
		return "http://127.0.0.1:" + server.getAddress().getPort() + "/hook";
	}
}
