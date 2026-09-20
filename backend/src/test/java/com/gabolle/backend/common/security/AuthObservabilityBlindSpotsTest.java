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
 * 401·403 이 아니라 {@code GlobalAuthExceptionHandler} 의 상태 코드 규칙에 안 걸리는 두 자리(429 로
 * 나가는 잠긴 계정 재시도, 400 으로 나가는 redirect URI 거부)가 실제로 로그에 남는지, 남으면서
 * 개인정보와 공격자가 준 문자열을 흘리지 않는지 본다.
 */
class AuthObservabilityBlindSpotsTest {

	private static final String EMAIL = "traveler@example.com";

	private Logger logbackLogger;

	private ListAppender<ILoggingEvent> appender;

	private Level originalLevel;

	private SecurityEventLogger securityEventLogger;

	@BeforeEach
	void setUp() {
		this.logbackLogger = (Logger) LoggerFactory.getLogger(SecurityEventLogger.class);
		// 수준을 명시한다. 앞선 Spring 테스트가 로그백을 재설정하면 아무것도 안 잡혀,
		// 아래 확인들이 빈 목록을 훑고 조용히 통과한다.
		this.originalLevel = this.logbackLogger.getLevel();
		this.logbackLogger.setLevel(Level.INFO);
		this.appender = new ListAppender<>();
		this.appender.start();
		this.logbackLogger.addAppender(this.appender);

		SecurityAlertNotifier notifier = new SecurityAlertNotifier(new SecurityAlertProperties(),
				RestClient.builder(), Clock.fixed(Instant.parse("2026-09-07T14:00:00Z"), ZoneOffset.UTC));
		this.securityEventLogger = new SecurityEventLogger(notifier);
	}

	@AfterEach
	void tearDown() {
		this.logbackLogger.detachAppender(this.appender);
		this.logbackLogger.setLevel(this.originalLevel);
	}

	@Test
	@DisplayName("🔴 이미 잠긴 계정에 온 시도가 남는다 — 이메일 원문 없이, 남은 잠금 시간과 함께")
	void lockedAccountAttemptIsRecordedWithoutTheRawEmail() {
		this.securityEventLogger.lockedAccountAttempt(EMAIL, 240);

		List<String> lines = linesContaining("event=AUTH_LOCKED_ACCOUNT_ATTEMPT");
		assertThat(lines)
				.withFailMessage("잠긴 계정에 온 시도가 로그에 없습니다. 이 확인이 빈 목록을 훑고 "
						+ "통과하면 관측이 없는 것과 같습니다.")
				.hasSize(1);

		String line = lines.get(0);
		assertThat(line).contains("emailHash=").contains("lockedForSeconds=240").contains("outcome=REJECTED");
		// 원문도, 앞부분도 남으면 안 된다 — 해시의 목적은 같은 주소의 반복을 세는 것뿐이다.
		assertThat(line).doesNotContain(EMAIL).doesNotContain("traveler");
	}

	@Test
	@DisplayName("🔴 거부된 redirect URI 는 호스트만 남는다 — 경로와 질의는 공격자가 정한 문자열이다")
	void rejectedRedirectUriLogsHostOnly() {
		this.securityEventLogger.oauthRedirectRejected("GOOGLE",
				"https://evil.example.com:8443/steal?token=abc123&victim=traveler%40example.com");

		List<String> lines = linesContaining("event=AUTH_OAUTH_REDIRECT_REJECTED");
		assertThat(lines).hasSize(1);

		String line = lines.get(0);
		assertThat(line).contains("provider=GOOGLE").contains("redirectHost=evil.example.com:8443");
		// 경로·질의를 그대로 적으면, 우리가 저장하지 않기로 한 것을 공격자가 우리 로그에 대신 적어 넣는다.
		assertThat(line).doesNotContain("/steal").doesNotContain("token=").doesNotContain("abc123")
				.doesNotContain("victim").doesNotContain("traveler");
	}

	@Test
	@DisplayName("파싱이 안 되는 redirect URI 도 원문을 남기지 않는다")
	void unparsableRedirectUriDoesNotLeakTheRawValue() {
		this.securityEventLogger.oauthRedirectRejected("NAVER", "not a uri at all ?token=abc123");

		List<String> lines = linesContaining("event=AUTH_OAUTH_REDIRECT_REJECTED");
		assertThat(lines).hasSize(1);
		assertThat(lines.get(0)).contains("redirectHost=unparsable").doesNotContain("abc123");
	}

	@Test
	@DisplayName("redirect URI 가 비어 있으면 none 으로 남는다 — 값이 없는 것과 못 읽은 것을 구분한다")
	void blankRedirectUriIsRecordedAsNone() {
		this.securityEventLogger.oauthRedirectRejected("KAKAO", "   ");

		assertThat(linesContaining("event=AUTH_OAUTH_REDIRECT_REJECTED")).hasSize(1);
		assertThat(linesContaining("redirectHost=none")).hasSize(1);
	}

	private List<String> linesContaining(String fragment) {
		return this.appender.list.stream().map(ILoggingEvent::getFormattedMessage)
				.filter((message) -> message.contains(fragment)).toList();
	}
}
