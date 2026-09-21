package com.gabolle.backend.functional;

import static org.assertj.core.api.Assertions.assertThat;

import com.gabolle.backend.auth.api.OAuthChallengeRequest;
import com.gabolle.backend.auth.api.OAuthLoginRequest;
import com.gabolle.backend.auth.service.OAuthChallengeService;
import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.functional.support.FunctionalJourneyTest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * 소셜 로그인 전면 차단의 재발을 잡는다. 허용 목록({@code SecurityConfig})과 매핑
 * ({@code AuthController})이 서로 다른 파일이라 한쪽만 고친 커밋이 git 에서 조용히 합쳐지고, 그러면
 * 챌린지 발급이 401 이 된다. 앱은 모든 401 을 비밀번호 오류 문구로 바꿔 보여줘서 증상이 원인을 가린다.
 * {@code MockMvc} 는 필터체인을 안 지나므로 이 사고를 못 잡는다.
 *
 * <p>실제 provider 왕복은 안 한다. client-id 가 비면 네트워크를 타기 전에 501 로 먼저 실패하므로,
 * 이 테스트는 그 501 을 "이 경로가 인증 없이 열려 있고 챌린지 소비까지 끝났다" 의 증거로 쓴다.
 */
class OAuthChallengeJourneyFunctionalTest extends FunctionalJourneyTest {

	private static final String REDIRECT_URI_TEMPLATE = "https://j15e201.p.ssafy.io/oauth/%s/callback";

	/**
	 * 인증 헤더 없이 챌린지를 발급받을 수 있어야 한다 — 401 이 나오면 허용 목록과 컨트롤러 매핑 중
	 * 하나가 다시 어긋난 것이다.
	 */
	@ParameterizedTest
	@ValueSource(strings = {"google", "naver", "kakao", "apple"})
	void challengeIssuedWithoutAuthentication(String provider) {
		String codeVerifier = codeVerifier();
		ResponseEntity<ApiResponse<OAuthChallengeService.IssuedChallenge>> response = rest.exchange(
				"/api/v1/auth/oauth/" + provider + "/challenge", HttpMethod.POST,
				new HttpEntity<>(challengeRequest(provider, codeVerifier)),
				new ParameterizedTypeReference<ApiResponse<OAuthChallengeService.IssuedChallenge>>() {
				});

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isNotNull();
		OAuthChallengeService.IssuedChallenge challenge = response.getBody().data();
		assertThat(challenge.state()).isNotBlank();
		assertThat(challenge.nonce()).isNotBlank();
		assertThat(challenge.expiresAt()).isAfter(java.time.Instant.now());
	}

	/**
	 * 챌린지뿐 아니라 로그인 엔드포인트 자체({@code POST /oauth/{provider}})도 인증 없이 열려
	 * 있어야 한다 — provider 미설정으로 501이 나오는 것과 인증 부재로 401이 나오는 것은 다르다.
	 * 이 테스트가 그 둘을 구분한다: 501이면 필터체인은 통과했고(=열려 있다) 그 다음 단계(실제 provider
	 * 설정)만 없는 것이다.
	 */
	@ParameterizedTest
	@ValueSource(strings = {"google", "naver", "kakao", "apple"})
	void loginEndpointReachableWithoutAuthentication(String provider) {
		String codeVerifier = codeVerifier();
		ResponseEntity<ApiResponse<OAuthChallengeService.IssuedChallenge>> issued = rest.exchange(
				"/api/v1/auth/oauth/" + provider + "/challenge", HttpMethod.POST,
				new HttpEntity<>(challengeRequest(provider, codeVerifier)),
				new ParameterizedTypeReference<ApiResponse<OAuthChallengeService.IssuedChallenge>>() {
				});
		OAuthChallengeService.IssuedChallenge challenge = issued.getBody().data();

		OAuthLoginRequest loginRequest = new OAuthLoginRequest("fake-authorization-code",
				String.format(REDIRECT_URI_TEMPLATE, provider), codeVerifier, challenge.state(), challenge.nonce(),
				null, "functional-test", java.util.Map.of("TERMS_OF_SERVICE", true, "PRIVACY_POLICY", true), false);
		ResponseEntity<ApiResponse<Object>> login = rest.exchange("/api/v1/auth/oauth/" + provider, HttpMethod.POST,
				new HttpEntity<>(loginRequest), new ParameterizedTypeReference<ApiResponse<Object>>() {
				});

		// 401 만 아니면 된다 — 정확히 어느 실패로 막히는지는 환경마다 다르다. client-id 가 비면
		// 501 로, 실제 credential 이 설정된 환경에서는 가짜 코드 교환이 실패해 502 로 막힌다.
		// 둘 다 "필터체인은 통과했다" 는 같은 사실의 증거다.
		assertThat(login.getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(login.getBody()).isNotNull();
		assertThat(login.getBody().error().code()).isIn("OAUTH_PROVIDER_NOT_CONFIGURED", "OAUTH_TOKEN_EXCHANGE_FAILED");
	}

	/**
	 * 반대쪽 경계 — {@code /oauth/{provider}/link} 는 인증 없이 쓸 수 있으면 안 된다.
	 * {@code /oauth/*} 는 한 마디짜리만 전부 여니, 인증이 필요한 경로는 두 마디여야 한다.
	 */
	@ParameterizedTest
	@ValueSource(strings = {"google", "naver", "kakao", "apple"})
	void authenticatedLinkEndpointStillRequiresAuthentication(String provider) {
		String codeVerifier = codeVerifier();
		OAuthLoginRequest linkRequest = new OAuthLoginRequest("fake-authorization-code",
				String.format(REDIRECT_URI_TEMPLATE, provider), codeVerifier, "irrelevant-state", "irrelevant-nonce",
				null, "functional-test", null, false);

		ResponseEntity<ApiResponse<Object>> response = rest.exchange("/api/v1/auth/oauth/" + provider + "/link",
				HttpMethod.POST, new HttpEntity<>(linkRequest), new ParameterizedTypeReference<ApiResponse<Object>>() {
				});

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	/** 허용되지 않은 redirect URI 는 인증 여부와 무관하게 그대로 거절돼야 한다. */
	@ParameterizedTest
	@ValueSource(strings = {"google", "naver", "kakao", "apple"})
	void challengeRejectsDisallowedRedirectUri(String provider) {
		OAuthChallengeRequest request = new OAuthChallengeRequest("https://evil.example.com/callback", codeVerifier(),
				"S256", "functional-test");

		ResponseEntity<ApiResponse<Object>> response = rest.exchange("/api/v1/auth/oauth/" + provider + "/challenge",
				HttpMethod.POST, new HttpEntity<>(request), new ParameterizedTypeReference<ApiResponse<Object>>() {
				});

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody().error().code()).isEqualTo("INVALID_OAUTH_REDIRECT_URI");
	}

	private OAuthChallengeRequest challengeRequest(String provider, String codeVerifier) {
		return new OAuthChallengeRequest(String.format(REDIRECT_URI_TEMPLATE, provider), toCodeChallenge(codeVerifier),
				"S256", "functional-test");
	}

	/** PKCE code_verifier — 43~128자, [A-Za-z0-9._~-] (OAuthChallengeService와 같은 제약). */
	private String codeVerifier() {
		String raw = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
		return raw.substring(0, 64);
	}

	private String toCodeChallenge(String codeVerifier) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
		}
		catch (java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
