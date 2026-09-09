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
 * S15P21E201-682 후속 — 관측이 없던 두 자리를 메운 것이 실제로 남는지, 그리고 남으면서
 * 개인정보와 공격자가 준 문자열을 흘리지 않는지 확인한다.
 *
 * <h2>왜 이 둘이 사각지대였나</h2>
 * {@code GlobalAuthExceptionHandler} 는 {@code AuthException} 을 <b>상태 코드로 보고</b> 남긴다 —
 * 401 이면 토큰 거부, 403 이면 인가 실패. 그래서 그 둘이 아닌 코드는 통째로 안 남았다.
 *
 * <p>잠긴 계정에 오는 요청은 429 다. 계정이 잠기는 <b>순간</b>은
 * {@link SecurityEventLogger#accountLocked} 가 남기지만 그 뒤로 계속 두드리는 시도는 아무 데도
 * 안 남아서, 잠금이 공격을 막고 있는지 아니면 공격자가 이미 떠났는지 알 수 없었다.
 *
 * <p>허용 목록에 없는 redirect URI 로 오는 챌린지 요청은 400 이다. 형식 오류와 같은 층에서
 * 거부되므로 역시 안 남았다. 🔴 그런데 이것은 오타가 아니라 대개 공격이다 — 통과하면 우리가
 * 발급한 표가 남의 주소로 간다.
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
		// 🔴 수준을 명시한다. 전체 빌드에서 앞선 Spring 테스트가 로그백을 재설정해 아무것도
		//    안 잡히면 아래 확인들이 빈 목록을 훑고 조용히 통과한다 — 그건 "문제가 없다" 가
		//    아니라 "안 봤다" 다. 원래 수준은 tearDown 에서 되돌린다.
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
		// 🔴 원문도, 앞부분(traveler)도 남으면 안 된다. 해시의 목적이 같은 주소의 반복을
		//    세는 것뿐이므로 사람이 읽을 수 있는 조각은 필요하지 않다.
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
		// 🔴 이것이 이 테스트의 요점이다. 경로·질의를 그대로 적으면, 우리가 저장하지 않기로 한
		//    것(표·남의 이메일)을 공격자가 우리 로그에 대신 적어 넣게 된다.
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
