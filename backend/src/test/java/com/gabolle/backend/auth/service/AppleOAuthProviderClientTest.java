package com.gabolle.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.gabolle.backend.auth.domain.AuthProvider;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/**
 * 애플 코드 교환.
 *
 * <p>확인하는 것은 셋이다. 서명해 만든 client secret 을 실어 보내는가, 신원을 {@code id_token} 에서만
 * 꺼내는가, 가려진 주소일 때 "유효" 라고 단정하지 않는가.
 */
class AppleOAuthProviderClientTest {

	private static final String TOKEN_URI = "https://appleid.apple.com/auth/token";
	private static final String CLIENT_ID = "io.ssafy.gabolle.web";
	private static final String REDIRECT_URI = "https://j15e201.p.ssafy.io/oauth/apple/callback";

	@Test
	void sendsSignedClientSecretAndReadsIdentityFromIdToken() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		AppleClientSecretFactory secretFactory = mock(AppleClientSecretFactory.class);
		when(secretFactory.currentClientSecret()).thenReturn("signed.client.secret");
		AppleIdTokenVerifier verifier = mock(AppleIdTokenVerifier.class);
		when(verifier.verify("apple-id-token", "nonce-1")).thenReturn(new AppleIdTokenVerifier.VerifiedIdentity(
				"apple-subject", "traveler@example.com", Boolean.TRUE, Boolean.FALSE));
		AppleOAuthProviderClient client = client(builder, secretFactory, verifier);

		server.expect(requestTo(TOKEN_URI))
				.andExpect(method(HttpMethod.POST))
				.andExpect(content().string(Matchers.allOf(
						Matchers.containsString("client_id=" + CLIENT_ID),
						Matchers.containsString("client_secret=signed.client.secret"),
						Matchers.containsString("code=apple-code"),
						Matchers.containsString("code_verifier=verifier-1"))))
				.andRespond(withSuccess(
						"{\"access_token\":\"apple-access\",\"id_token\":\"apple-id-token\",\"token_type\":\"Bearer\"}",
						MediaType.APPLICATION_JSON));

		OAuthProviderClient.OAuthUserProfile profile = client.exchangeAuthorizationCode("apple-code", REDIRECT_URI,
				"verifier-1", "state-1", "nonce-1");

		assertThat(client.provider()).isEqualTo(AuthProvider.APPLE);
		assertThat(profile.subject()).isEqualTo("apple-subject");
		assertThat(profile.email()).isEqualTo("traveler@example.com");
		// 애플은 이름을 id_token 에 넣지 않는다 — 가입 화면에서 사용자가 적는다.
		assertThat(profile.displayName()).isNull();
		assertThat(profile.language()).isEqualTo("KO");
		assertThat(profile.emailVerified()).isTrue();
		assertThat(profile.emailValid()).isTrue();
		server.verify();
	}

	@Test
	void leavesValidityUnknownForHiddenRelayAddress() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		AppleClientSecretFactory secretFactory = mock(AppleClientSecretFactory.class);
		when(secretFactory.currentClientSecret()).thenReturn("signed.client.secret");
		AppleIdTokenVerifier verifier = mock(AppleIdTokenVerifier.class);
		when(verifier.verify("apple-id-token", "nonce-1")).thenReturn(new AppleIdTokenVerifier.VerifiedIdentity(
				"apple-subject", "hidden@privaterelay.appleid.com", Boolean.TRUE, Boolean.TRUE));
		AppleOAuthProviderClient client = client(builder, secretFactory, verifier);

		server.expect(requestTo(TOKEN_URI)).andRespond(withSuccess(
				"{\"access_token\":\"apple-access\",\"id_token\":\"apple-id-token\"}", MediaType.APPLICATION_JSON));

		OAuthProviderClient.OAuthUserProfile profile = client.exchangeAuthorizationCode("apple-code", REDIRECT_URI,
				"verifier-1", "state-1", "nonce-1");

		assertThat(profile.email()).isEqualTo("hidden@privaterelay.appleid.com");
		assertThat(profile.emailVerified()).isTrue();
		// 지금은 전달되지만 사용자가 전달을 끄면 죽는 주소다. 모른다고 남긴다.
		assertThat(profile.emailValid()).isNull();
	}

	@Test
	void answersNotImplementedWhenSigningKeyIsMissing() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer.bindTo(builder).build();
		AppleClientSecretFactory secretFactory = mock(AppleClientSecretFactory.class);
		when(secretFactory.currentClientSecret()).thenThrow(new AuthException("OAUTH_PROVIDER_NOT_CONFIGURED",
				"Apple client 설정이 없습니다.", HttpStatus.NOT_IMPLEMENTED));
		AppleIdTokenVerifier verifier = mock(AppleIdTokenVerifier.class);
		AppleOAuthProviderClient client = client(builder, secretFactory, verifier);

		assertThatThrownBy(() -> client.exchangeAuthorizationCode("apple-code", REDIRECT_URI, "verifier-1", "state-1",
				"nonce-1"))
				.isInstanceOf(AuthException.class)
				.extracting(exception -> ((AuthException) exception).getStatus())
				.isEqualTo(HttpStatus.NOT_IMPLEMENTED);
	}

	@Test
	void failsWhenAppleReturnsNoIdToken() {
		// 신원이 id_token 에만 있으므로, 그것이 없으면 로그인시킬 근거가 없다.
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		AppleClientSecretFactory secretFactory = mock(AppleClientSecretFactory.class);
		when(secretFactory.currentClientSecret()).thenReturn("signed.client.secret");
		AppleIdTokenVerifier verifier = mock(AppleIdTokenVerifier.class);
		when(verifier.verify(any(), any())).thenThrow(new AuthException("OAUTH_ID_TOKEN_INVALID",
				"Apple ID Token 검증에 실패했습니다.", HttpStatus.BAD_GATEWAY));
		AppleOAuthProviderClient client = client(builder, secretFactory, verifier);

		server.expect(requestTo(TOKEN_URI))
				.andRespond(withSuccess("{\"access_token\":\"apple-access\"}", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> client.exchangeAuthorizationCode("apple-code", REDIRECT_URI, "verifier-1", "state-1",
				"nonce-1"))
				.isInstanceOf(AuthException.class)
				.extracting(exception -> ((AuthException) exception).getCode())
				.isEqualTo("OAUTH_ID_TOKEN_INVALID");
	}

	@Test
	void springCreatesClientUsingConfiguredConstructor() {
		// 설정 이름이 하나라도 어긋나면 기동에서 죽는다. 그 자리를 여기서 잡는다.
		try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
			context.getEnvironment().setActiveProfiles("dev");
			TestPropertyValues.of(
					"gabolle.oauth.apple.client-id=" + CLIENT_ID,
					"gabolle.oauth.apple.team-id=TEAM123456",
					"gabolle.oauth.apple.key-id=KEY7890AB",
					"gabolle.oauth.apple.private-key=irrelevant-for-wiring",
					"gabolle.oauth.apple.jwk-set-uri=https://example.com/auth/keys")
				.applyTo(context);
			context.register(HttpBeans.class, AppleClientSecretFactory.class, AppleIdTokenVerifier.class,
					AppleOAuthProviderClient.class);

			context.refresh();

			assertThat(context.getBean(AppleOAuthProviderClient.class).provider()).isEqualTo(AuthProvider.APPLE);
			assertThat(context.getBean(AppleClientSecretFactory.class).isConfigured()).isTrue();
		}
	}

	@Configuration
	static class HttpBeans {

		@Bean
		RestClient.Builder restClientBuilder() {
			return RestClient.builder();
		}

		@Bean
		ObjectMapper objectMapper() {
			return new ObjectMapper();
		}

	}

	private AppleOAuthProviderClient client(RestClient.Builder builder, AppleClientSecretFactory secretFactory,
			AppleIdTokenVerifier verifier) {
		return new AppleOAuthProviderClient(builder, new ObjectMapper(), CLIENT_ID, secretFactory, verifier, null);
	}
}
