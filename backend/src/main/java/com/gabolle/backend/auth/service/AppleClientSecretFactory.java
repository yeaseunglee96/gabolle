package com.gabolle.backend.auth.service;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * 애플에 낼 {@code client_secret} 을 만든다 — S15P21E201-825.
 *
 * <p>🔴 <b>여기가 애플이 다른 셋과 갈라지는 자리다.</b> 구글·카카오·네이버는 콘솔에서 받은 문자열을
 * 설정에 넣으면 그만인데, 애플은 {@code client_secret} 자리에 <b>우리가 서명한 JWT</b> 를 요구한다.
 * 서명 키는 개발자 계정에서 한 번 내려받는 {@code .p8}(EC P-256 개인키)이고, 토큰의 수명은 최대
 * 6개월이다. 즉 설정에 박아 둘 수 있는 값이 아니다 — 박아 두면 그 배포는 <b>최대 6개월 뒤 어느 날
 * 애플 로그인만 전부 실패한다.</b> 그래서 서버가 직접 만들고, 만료 전에 다시 만든다.
 *
 * <p>클레임은 애플이 정한 그대로다: {@code iss} 는 팀 번호, {@code sub} 는 client id(웹은 Service ID,
 * 앱은 번들 id), {@code aud} 는 항상 {@code https://appleid.apple.com}, 헤더의 {@code kid} 는 키 번호.
 * 하나라도 어긋나면 애플은 {@code invalid_client} 만 돌려주고 무엇이 틀렸는지 알려주지 않는다.
 *
 * <p>만든 표는 만료 조금 전까지 재사용한다. 요청마다 새로 서명해도 애플은 받아 주지만, 서명은 공짜가
 * 아니고 로그인마다 도는 자리라 캐시가 맞다. 다만 <b>메모리에만</b> 둔다 — 서버가 다시 뜨면 새로
 * 만들면 되는 값이라 어디에도 안 적는다(적으면 그게 유출 자리가 된다).
 */
@Component
@Profile({"db", "dev"})
public class AppleClientSecretFactory {

	/** 애플이 요구하는 고정 수신자. 이 값은 설정으로 열지 않는다 — 애플 말고 받을 곳이 없다. */
	private static final String APPLE_AUDIENCE = "https://appleid.apple.com";

	/** 만료 이 시간 전부터는 새로 만든다. 교환 도중에 만료되는 일을 막는 여유다. */
	private static final Duration RENEW_BEFORE_EXPIRY = Duration.ofMinutes(5);

	/** 애플이 허용하는 상한(6개월). 설정이 이보다 길면 여기서 잘라 낸다. */
	private static final Duration MAX_TTL = Duration.ofDays(180);

	// 🔴 수명을 "90d" 같은 글자가 아니라 날 수(숫자)로 받는다. 문자열을 Duration 으로 바꾸는 일은
	//    스프링 부트가 끼워 주는 변환기가 있어야 되는데, 그 변환기가 없는 자리(예: 얇은 스프링
	//    컨텍스트)에서는 기동이 그대로 죽는다. 설정 하나 때문에 서버가 안 뜨는 것은 너무 비싸다.

	private final String teamId;
	private final String keyId;
	private final String clientId;
	private final String privateKeyPem;
	private final Duration ttl;
	private final Clock clock;

	private volatile CachedSecret cached;

	@Autowired
	public AppleClientSecretFactory(@Value("${gabolle.oauth.apple.team-id:}") String teamId,
			@Value("${gabolle.oauth.apple.key-id:}") String keyId,
			@Value("${gabolle.oauth.apple.client-id:}") String clientId,
			@Value("${gabolle.oauth.apple.private-key:}") String privateKeyPem,
			@Value("${gabolle.oauth.apple.client-secret-ttl-days:90}") long ttlDays) {
		this(teamId, keyId, clientId, privateKeyPem, Duration.ofDays(ttlDays), Clock.systemUTC());
	}

	AppleClientSecretFactory(String teamId, String keyId, String clientId, String privateKeyPem, Duration ttl,
			Clock clock) {
		this.teamId = teamId;
		this.keyId = keyId;
		this.clientId = clientId;
		this.privateKeyPem = privateKeyPem;
		this.ttl = (ttl == null || ttl.isZero() || ttl.isNegative() || ttl.compareTo(MAX_TTL) > 0) ? MAX_TTL : ttl;
		this.clock = clock;
	}

	/** 설정이 다 있는가. 없으면 애플만 501 로 답하고 나머지 로그인은 그대로 돈다. */
	public boolean isConfigured() {
		return hasText(teamId) && hasText(keyId) && hasText(clientId) && hasText(privateKeyPem);
	}

	/**
	 * 지금 쓸 수 있는 {@code client_secret} 을 준다. 캐시된 것이 아직 넉넉하면 그것을 그대로 준다.
	 */
	public synchronized String currentClientSecret() {
		if (!isConfigured()) {
			throw new AuthException("OAUTH_PROVIDER_NOT_CONFIGURED", "Apple client 설정이 없습니다.",
					HttpStatus.NOT_IMPLEMENTED);
		}
		Instant now = clock.instant();
		CachedSecret current = this.cached;
		if (current != null && now.isBefore(current.expiresAt().minus(RENEW_BEFORE_EXPIRY))) {
			return current.value();
		}
		CachedSecret fresh = sign(now);
		this.cached = fresh;
		return fresh.value();
	}

	private CachedSecret sign(Instant now) {
		Instant expiresAt = now.plus(ttl);
		JWTClaimsSet claims = new JWTClaimsSet.Builder()
				.issuer(teamId.trim())
				.subject(clientId.trim())
				.audience(APPLE_AUDIENCE)
				.issueTime(Date.from(now))
				.expirationTime(Date.from(expiresAt))
				.build();
		JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(keyId.trim()).build();
		SignedJWT jwt = new SignedJWT(header, claims);
		try {
			jwt.sign(new ECDSASigner(readPrivateKey()));
		} catch (JOSEException exception) {
			// 🔴 예외 메시지에 키를 싣지 않는다. 여기서 실패하는 이유는 대개 키 형식이고,
			//    그 내용을 로그에 남기면 개인키가 로그로 새어 나간다.
			throw misconfigured();
		}
		return new CachedSecret(jwt.serialize(), expiresAt);
	}

	/**
	 * {@code .p8} 파일의 내용을 개인키로 읽는다.
	 *
	 * <p>🔴 환경변수로 넘어오면서 줄바꿈이 {@code \n} 두 글자로 바뀌어 오는 일이 흔하다. 그것까지
	 * 되돌려 준다 — 안 그러면 "설정은 넣었는데 계속 실패" 가 되고, 원인이 눈에 안 보인다.
	 */
	private ECPrivateKey readPrivateKey() {
		String normalized = privateKeyPem.replace("\\n", "\n")
				.replace("-----BEGIN PRIVATE KEY-----", "")
				.replace("-----END PRIVATE KEY-----", "")
				.replaceAll("\\s", "");
		try {
			byte[] der = Base64.getDecoder().decode(normalized);
			KeyFactory keyFactory = KeyFactory.getInstance("EC");
			return (ECPrivateKey) keyFactory.generatePrivate(new PKCS8EncodedKeySpec(der));
		} catch (GeneralSecurityException | IllegalArgumentException | ClassCastException exception) {
			throw misconfigured();
		}
	}

	private AuthException misconfigured() {
		return new AuthException("OAUTH_APPLE_KEY_INVALID", "Apple 서명 키를 읽지 못했습니다.",
				HttpStatus.INTERNAL_SERVER_ERROR);
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private record CachedSecret(String value, Instant expiresAt) {
	}
}
