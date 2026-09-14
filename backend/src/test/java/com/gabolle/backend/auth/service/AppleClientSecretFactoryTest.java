package com.gabolle.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * 애플 {@code client_secret} 서명 — S15P21E201-825.
 *
 * <p>이 표가 틀리면 애플은 {@code invalid_client} 만 돌려주고 어디가 틀렸는지 말해 주지 않는다.
 * 그래서 헤더·클레임·수명을 여기서 하나씩 확인한다.
 */
class AppleClientSecretFactoryTest {

	private static final String TEAM_ID = "TEAM123456";
	private static final String KEY_ID = "KEY7890AB";
	private static final String CLIENT_ID = "io.ssafy.gabolle.web";

	private static KeyPair keyPair;
	private static String privateKeyPem;

	@BeforeAll
	static void generateSigningKey() throws Exception {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
		generator.initialize(new ECGenParameterSpec("secp256r1"));
		keyPair = generator.generateKeyPair();
		privateKeyPem = "-----BEGIN PRIVATE KEY-----\n"
				+ Base64.getMimeEncoder(64, new byte[] { '\n' }).encodeToString(keyPair.getPrivate().getEncoded())
				+ "\n-----END PRIVATE KEY-----\n";
	}

	@Test
	void signsSecretWithClaimsAppleRequires() throws Exception {
		Instant now = Instant.parse("2026-09-10T00:00:00Z");
		AppleClientSecretFactory factory = factory(privateKeyPem, Duration.ofDays(90), new MutableClock(now));

		SignedJWT jwt = SignedJWT.parse(factory.currentClientSecret());

		assertThat(jwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.ES256);
		assertThat(jwt.getHeader().getKeyID()).isEqualTo(KEY_ID);
		assertThat(jwt.verify(new ECDSAVerifier((ECPublicKey) keyPair.getPublic()))).isTrue();
		assertThat(jwt.getJWTClaimsSet().getIssuer()).isEqualTo(TEAM_ID);
		assertThat(jwt.getJWTClaimsSet().getSubject()).isEqualTo(CLIENT_ID);
		assertThat(jwt.getJWTClaimsSet().getAudience()).containsExactly("https://appleid.apple.com");
		assertThat(jwt.getJWTClaimsSet().getExpirationTime().toInstant()).isEqualTo(now.plus(Duration.ofDays(90)));
	}

	@Test
	void reusesSecretUntilRenewWindowAndSignsAgainAfterIt() {
		MutableClock clock = new MutableClock(Instant.parse("2026-09-10T00:00:00Z"));
		AppleClientSecretFactory factory = factory(privateKeyPem, Duration.ofHours(1), clock);

		String first = factory.currentClientSecret();
		clock.advance(Duration.ofMinutes(50));
		assertThat(factory.currentClientSecret()).isEqualTo(first);

		// 만료 5분 전부터는 새로 만든다 — 교환 도중에 만료되는 것을 막는 여유다.
		clock.advance(Duration.ofMinutes(6));
		assertThat(factory.currentClientSecret()).isNotEqualTo(first);
	}

	@Test
	void capsLifetimeAtAppleMaximum() throws Exception {
		Instant now = Instant.parse("2026-09-10T00:00:00Z");
		AppleClientSecretFactory factory = factory(privateKeyPem, Duration.ofDays(400), new MutableClock(now));

		SignedJWT jwt = SignedJWT.parse(factory.currentClientSecret());

		assertThat(jwt.getJWTClaimsSet().getExpirationTime().toInstant()).isEqualTo(now.plus(Duration.ofDays(180)));
	}

	@Test
	void readsKeyWhoseNewlinesArrivedEscaped() {
		// 환경변수로 넘어오면서 줄바꿈이 \n 두 글자가 되는 흔한 사고.
		String escaped = privateKeyPem.replace("\n", "\\n");
		AppleClientSecretFactory factory = factory(escaped, Duration.ofDays(90),
				new MutableClock(Instant.parse("2026-09-10T00:00:00Z")));

		assertThat(factory.currentClientSecret()).isNotBlank();
	}

	@Test
	void answersNotImplementedWhenAppleIsNotConfigured() {
		AppleClientSecretFactory factory = new AppleClientSecretFactory("", "", "", "", Duration.ofDays(90),
				Clock.systemUTC());

		assertThat(factory.isConfigured()).isFalse();
		assertThatThrownBy(factory::currentClientSecret)
				.isInstanceOf(AuthException.class)
				.extracting(exception -> ((AuthException) exception).getStatus())
				.isEqualTo(HttpStatus.NOT_IMPLEMENTED);
	}

	@Test
	void rejectsKeyThatIsNotAPrivateKey() {
		AppleClientSecretFactory factory = factory("not-a-key", Duration.ofDays(90), Clock.systemUTC());

		assertThatThrownBy(factory::currentClientSecret)
				.isInstanceOf(AuthException.class)
				.extracting(exception -> ((AuthException) exception).getCode())
				.isEqualTo("OAUTH_APPLE_KEY_INVALID");
	}

	private AppleClientSecretFactory factory(String key, Duration ttl, Clock clock) {
		return new AppleClientSecretFactory(TEAM_ID, KEY_ID, CLIENT_ID, key, ttl, clock);
	}

	/** 만료·갱신을 시계 없이 확인할 수 없어 시간을 손으로 옮긴다. */
	private static final class MutableClock extends Clock {

		private Instant now;

		private MutableClock(Instant now) {
			this.now = now;
		}

		private void advance(Duration amount) {
			this.now = this.now.plus(amount);
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}
	}
}
