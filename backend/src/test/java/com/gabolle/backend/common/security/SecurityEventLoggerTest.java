package com.gabolle.backend.common.security;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 로그 줄이 구조화됐는지와 개인정보·비밀값이 새지 않는지 본다. {@link ListAppender} 를 로거에 직접
 * 붙여 실제로 나간 줄을 검사한다.
 */
class SecurityEventLoggerTest {

	private static final String RAW_EMAIL = "traveler@example.com";

	private Logger logbackLogger;
	private ListAppender<ILoggingEvent> appender;
	private SecurityEventLogger securityEventLogger;

	@BeforeEach
	void setUp() {
		this.logbackLogger = (Logger) LoggerFactory.getLogger(SecurityEventLogger.class);
		this.appender = new ListAppender<>();
		this.appender.start();
		this.logbackLogger.addAppender(this.appender);

		SecurityAlertProperties properties = new SecurityAlertProperties(); // webhookUrl 기본값 빈 문자열
		Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
		SecurityAlertNotifier notifier = new SecurityAlertNotifier(properties, RestClient.builder(), clock);
		this.securityEventLogger = new SecurityEventLogger(notifier);
	}

	@AfterEach
	void tearDown() {
		this.logbackLogger.detachAppender(this.appender);
	}

	@Test
	@DisplayName("로그인 실패는 INFO 로, key=value 형태로 남는다")
	void loginFailureIsStructuredAndInfoLevel() {
		this.securityEventLogger.loginFailure(RAW_EMAIL, 3);

		ILoggingEvent event = onlyEvent();
		assertThat(event.getLevel()).isEqualTo(Level.INFO);
		String message = event.getFormattedMessage();
		assertThat(message).contains("event=AUTH_LOGIN_FAILURE").contains("attempts=3").contains("outcome=REJECTED")
				.containsPattern("emailHash=[0-9a-f]{12}").contains("remoteIp=unknown");
	}

	@Test
	@DisplayName("계정 잠금은 WARN 으로 남는다 — 실패 한 건과 구분된다")
	void accountLockedIsWarnLevel() {
		this.securityEventLogger.accountLocked(RAW_EMAIL, 5);

		ILoggingEvent event = onlyEvent();
		assertThat(event.getLevel()).isEqualTo(Level.WARN);
		assertThat(event.getFormattedMessage()).contains("event=AUTH_ACCOUNT_LOCKED").contains("attempts=5")
				.contains("outcome=LOCKED");
	}

	@Test
	@DisplayName("토큰 거부·인가 실패도 key=value 로 남는다")
	void tokenRejectedAndAuthzDeniedAreStructured() {
		this.securityEventLogger.tokenRejected("AUTHENTICATION_REQUIRED");
		this.securityEventLogger.authzDenied("ACCOUNT_UNAVAILABLE");

		List<String> messages = this.appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
		assertThat(messages.get(0)).contains("event=AUTH_TOKEN_REJECTED").contains("reason=AUTHENTICATION_REQUIRED");
		assertThat(messages.get(1)).contains("event=AUTHZ_DENIED").contains("reason=ACCOUNT_UNAVAILABLE");
	}

	@Test
	@DisplayName("🔴 완료 기준 — 이메일 원문이 어느 로그 줄에도 없다")
	void rawEmailNeverAppearsInAnyLogLine() {
		this.securityEventLogger.loginFailure(RAW_EMAIL, 1);
		this.securityEventLogger.loginFailure(RAW_EMAIL, 2);
		this.securityEventLogger.accountLocked(RAW_EMAIL, 5);

		for (ILoggingEvent event : this.appender.list) {
			assertThat(event.getFormattedMessage()).doesNotContain(RAW_EMAIL).doesNotContain("traveler");
		}
	}

	@Test
	@DisplayName("비밀번호는 애초에 매개변수로 받지 않으므로 로그에 나갈 수 없다")
	void passwordNeverAppearsInAnyLogLine() {
		String password = "Route!2026SuperSecret";
		this.securityEventLogger.loginFailure(RAW_EMAIL, 1);

		assertThat(this.appender.list).allSatisfy(
				event -> assertThat(event.getFormattedMessage()).doesNotContain(password));
	}

	@Test
	@DisplayName("같은 이메일은 항상 같은 해시로 남는다 — 반복 실패를 셀 수 있어야 한다")
	void sameEmailHashesToTheSameValue() {
		this.securityEventLogger.loginFailure(RAW_EMAIL, 1);
		this.securityEventLogger.loginFailure(RAW_EMAIL, 2);

		String firstHash = extractField(this.appender.list.get(0).getFormattedMessage(), "emailHash");
		String secondHash = extractField(this.appender.list.get(1).getFormattedMessage(), "emailHash");
		assertThat(firstHash).isEqualTo(secondHash).hasSize(12);
	}

	private ILoggingEvent onlyEvent() {
		assertThat(this.appender.list).hasSize(1);
		return this.appender.list.get(0);
	}

	private String extractField(String message, String field) {
		String marker = field + "=";
		int start = message.indexOf(marker) + marker.length();
		int end = message.indexOf(' ', start);
		return end == -1 ? message.substring(start) : message.substring(start, end);
	}
}
