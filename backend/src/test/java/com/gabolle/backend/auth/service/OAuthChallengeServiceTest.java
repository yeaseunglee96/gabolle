package com.gabolle.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.gabolle.backend.common.security.SecurityEventLogger;
import com.gabolle.backend.common.security.SecurityAlertProperties;
import com.gabolle.backend.common.security.SecurityAlertNotifier;
import com.gabolle.backend.auth.config.AuthProperties;
import com.gabolle.backend.auth.domain.AuthProvider;
import com.gabolle.backend.auth.domain.OAuthChallenge;
import com.gabolle.backend.auth.repository.OAuthChallengeRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Optional;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OAuthChallengeServiceTest {

	@Mock private OAuthChallengeRepository repository;

	private OAuthChallengeService service;
	private SessionTokenGenerator tokenGenerator;
	private AuthProperties properties;
	private Instant now;
	private ch.qos.logback.classic.Logger logbackLogger;
	private ListAppender<ILoggingEvent> appender;
	private Level originalLevel;
	private static final String REDIRECT_URI = "https://j15e201.p.ssafy.io/oauth/google/callback";
	private static final String KAKAO_REDIRECT_URI = "https://j15e201.p.ssafy.io/oauth/kakao/callback";

	@BeforeEach
	void setUp() {
		now = Instant.parse("2026-01-01T00:00:00Z");
		tokenGenerator = new SessionTokenGenerator();
		properties = new AuthProperties();
		properties.getOauthAllowedRedirectUris().add(REDIRECT_URI);
		properties.getOauthAllowedRedirectUris().add(KAKAO_REDIRECT_URI);
		// 수준을 명시하고 원래대로 되돌린다. 앞선 Spring 검사가 로그백을 재설정하면
		// 아래 로깅 확인이 빈 목록을 훑고 조용히 통과한다.
		logbackLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(SecurityEventLogger.class);
		originalLevel = logbackLogger.getLevel();
		logbackLogger.setLevel(Level.INFO);
		appender = new ListAppender<>();
		appender.start();
		logbackLogger.addAppender(appender);

		service = new OAuthChallengeService(repository, tokenGenerator, properties,
				new SecurityEventLogger(new SecurityAlertNotifier(new SecurityAlertProperties(),
						org.springframework.web.client.RestClient.builder(), Clock.systemUTC())),
				Clock.fixed(now, ZoneOffset.UTC));
	}

	@AfterEach
	void tearDown() {
		logbackLogger.detachAppender(appender);
		logbackLogger.setLevel(originalLevel);
	}

	@Test
	void rejectedRedirectUriIsRecordedAsASecurityEvent() {
		// 이 거부는 400 이라 상태 코드만 보는 로깅에는 안 잡힌다. 통과하면 우리가 발급한
		// 표가 남의 주소로 가므로 오타가 아니라 대개 공격이다.
		assertThatThrownBy(() -> service.issue(AuthProvider.GOOGLE,
				"https://evil.example.com/steal?token=abc123", codeChallenge("verifier-" + "x".repeat(40)),
				"S256", "device-1"))
				.isInstanceOfSatisfying(AuthException.class,
						(e) -> assertThat(e.getCode()).isEqualTo("INVALID_OAUTH_REDIRECT_URI"));

		List<String> lines = appender.list.stream().map(ILoggingEvent::getFormattedMessage)
				.filter((message) -> message.contains("event=AUTH_OAUTH_REDIRECT_REJECTED")).toList();
		assertThat(lines)
				.withFailMessage("허용 목록에 없는 redirect URI 요청이 로그에 없습니다. 배선이 빠지면 "
						+ "이 공격 시도가 통째로 안 보입니다.")
				.hasSize(1);
		assertThat(lines.get(0)).contains("provider=GOOGLE").contains("redirectHost=evil.example.com");
		// 공격자가 정한 경로·질의는 우리 로그에 들어가지 않는다.
		assertThat(lines.get(0)).doesNotContain("abc123").doesNotContain("/steal");
	}

	@Test
	void challengeCanBeConsumedOnlyWithBoundRequestValues() {
		when(repository.save(any(OAuthChallenge.class))).thenAnswer(invocation -> invocation.getArgument(0));
		String verifier = "verifier-abcdefghijklmnopqrstuvwxyz-0123456789-abcdef";
		String challenge = codeChallenge(verifier);
		OAuthChallengeService.IssuedChallenge issued = service.issue(AuthProvider.GOOGLE,
				REDIRECT_URI, challenge, "S256", "device-1");
		OAuthChallenge stored = OAuthChallenge.issue(AuthProvider.GOOGLE, tokenGenerator.hash(issued.state()),
				tokenGenerator.hash(issued.nonce()), tokenGenerator.hash(challenge), "S256", REDIRECT_URI,
				"device-1", issued.expiresAt());
		when(repository.findByProviderAndStateHash(AuthProvider.GOOGLE, tokenGenerator.hash(issued.state())))
				.thenReturn(Optional.of(stored));

		service.consume(AuthProvider.GOOGLE, issued.state(), issued.nonce(), verifier, REDIRECT_URI,
				"device-1");

		assertThatThrownBy(() -> service.consume(AuthProvider.GOOGLE, issued.state(), issued.nonce(), verifier,
				REDIRECT_URI, "device-1"))
				.isInstanceOf(AuthException.class).hasMessageContaining("유효하지 않거나 만료된");
	}

	@Test
	void rejectsMismatchedRedirectUri() {
		String state = "state";
		String nonce = "nonce";
		String verifier = "verifier-abcdefghijklmnopqrstuvwxyz-0123456789-abcdef";
		String challenge = codeChallenge(verifier);
		OAuthChallenge stored = OAuthChallenge.issue(AuthProvider.KAKAO, tokenGenerator.hash(state),
				tokenGenerator.hash(nonce), tokenGenerator.hash(challenge), "S256", KAKAO_REDIRECT_URI, "device-1",
				now.plusSeconds(60));
		when(repository.findByProviderAndStateHash(AuthProvider.KAKAO, tokenGenerator.hash(state)))
				.thenReturn(Optional.of(stored));

		assertThatThrownBy(() -> service.consume(AuthProvider.KAKAO, state, nonce, verifier,
				"https://attacker.example/callback", "device-1"))
				.isInstanceOf(AuthException.class);
	}

	@Test
	void rejectsRedirectUriOutsideConfiguredAllowList() {
		String verifier = "verifier-abcdefghijklmnopqrstuvwxyz-0123456789-abcdef";
		assertThatThrownBy(() -> service.issue(AuthProvider.GOOGLE, "https://evil.example/callback",
				codeChallenge(verifier), "S256", "device-1"))
				.isInstanceOf(AuthException.class)
				.hasMessageContaining("redirect URI");
	}

	@Test
	void rejectsPlainPkceMethod() {
		String verifier = "verifier-abcdefghijklmnopqrstuvwxyz-0123456789-abcdef";
		assertThatThrownBy(() -> service.issue(AuthProvider.GOOGLE, REDIRECT_URI, codeChallenge(verifier), "plain",
				"device-1"))
				.isInstanceOf(AuthException.class)
				.hasMessageContaining("PKCE");
	}

	private String codeChallenge(String verifier) {
		try {
			return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256")
					.digest(verifier.getBytes(StandardCharsets.US_ASCII)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}
}
