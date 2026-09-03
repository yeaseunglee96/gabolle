package com.gabolle.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.gabolle.backend.auth.domain.AuthProvider;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class NaverOAuthProviderClientTest {

	@Test
	void includesStateWhenExchangingNaverCode() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		NaverOAuthProviderClient client = new NaverOAuthProviderClient(builder, new ObjectMapper(), "naver-client-id",
				"naver-client-secret", null);

		server.expect(requestTo("https://nid.naver.com/oauth2.0/token"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(content().string(org.hamcrest.Matchers.allOf(
						org.hamcrest.Matchers.containsString("client_id=naver-client-id"),
						org.hamcrest.Matchers.containsString("client_secret=naver-client-secret"),
						org.hamcrest.Matchers.containsString("state=state-1"))))
				.andRespond(withSuccess("{\"access_token\":\"naver-access\"}", MediaType.APPLICATION_JSON));
		server.expect(requestTo("https://openapi.naver.com/v1/nid/me"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(
						"{\"resultcode\":\"00\",\"message\":\"success\",\"response\":{\"id\":\"naver-subject\",\"email\":\"traveler@example.com\",\"name\":\"Traveler\",\"locale\":\"en_US\"}}",
						MediaType.APPLICATION_JSON));

		OAuthProviderClient.OAuthUserProfile profile = client.exchangeAuthorizationCode("naver-code",
				"https://j15e201.p.ssafy.io/oauth/naver/callback", "verifier-1", "state-1", "nonce-1");

		assertThat(client.provider()).isEqualTo(AuthProvider.NAVER);
		assertThat(profile.subject()).isEqualTo("naver-subject");
		assertThat(profile.email()).isEqualTo("traveler@example.com");
		assertThat(profile.displayName()).isEqualTo("Traveler");
		assertThat(profile.language()).isEqualTo("EN");
		server.verify();
	}
}
