package com.gabolle.backend.functional;

import static org.assertj.core.api.Assertions.assertThat;

import com.gabolle.backend.auth.api.AuthUserResponse;
import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.functional.support.AuthedClient;
import com.gabolle.backend.functional.support.FunctionalJourneyTest;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * 하네스(S15P21E201-779)가 실제로 도는지 증명하는 예시 시나리오.
 *
 * <p>회원가입 → 이메일 인증(DB 직접) → 로그인 → 인증이 필요한 경로 호출까지 실제 소켓과 실제
 * {@code SecurityFilterChain}을 통과시킨다. MockMvc는 이 필터체인을 절대 안 통과하므로, 이
 * 테스트가 통과한다는 것 자체가 하네스의 핵심 가치(01절 — 실제 HTTP만 잡는 실패 유형)를
 * 증명한다.
 */
class AuthedMeFunctionalTest extends FunctionalJourneyTest {

	@Test
	void signupVerifyLoginThenReadOwnProfile() {
		AuthedClient authed = loginAsNewUser("harness-proof");

		ResponseEntity<ApiResponse<AuthUserResponse>> me = authed.get("/api/v1/auth/me",
				new ParameterizedTypeReference<ApiResponse<AuthUserResponse>>() {
				});

		assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(me.getBody()).isNotNull();
		assertThat(me.getBody().data().email()).contains("harness-proof");
	}

	@Test
	void meWithoutTokenIsRejected() {
		ResponseEntity<String> response = rest.getForEntity("/api/v1/auth/me", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}
}
