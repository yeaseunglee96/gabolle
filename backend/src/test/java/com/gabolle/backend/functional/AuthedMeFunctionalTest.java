package com.gabolle.backend.functional;

import static org.assertj.core.api.Assertions.assertThat;

import com.gabolle.backend.auth.api.AuthUserResponse;
import com.gabolle.backend.auth.domain.AuthIdentity;
import com.gabolle.backend.auth.domain.AuthProvider;
import com.gabolle.backend.auth.domain.LocalCredential;
import com.gabolle.backend.auth.repository.AuthIdentityRepository;
import com.gabolle.backend.auth.repository.LocalCredentialRepository;
import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.functional.support.AuthedClient;
import com.gabolle.backend.functional.support.FunctionalJourneyTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 하네스(S15P21E201-779)가 실제로 도는지 증명하는 예시 시나리오.
 *
 * <p>회원가입 → 이메일 인증(DB 직접) → 로그인 → 인증이 필요한 경로 호출까지 실제 소켓과 실제
 * {@code SecurityFilterChain}을 통과시킨다. MockMvc는 이 필터체인을 절대 안 통과하므로, 이
 * 테스트가 통과한다는 것 자체가 하네스의 핵심 가치(01절 — 실제 HTTP만 잡는 실패 유형)를
 * 증명한다.
 */
class AuthedMeFunctionalTest extends FunctionalJourneyTest {

	@Autowired
	private LocalCredentialRepository credentialRepository;

	@Autowired
	private AuthIdentityRepository identityRepository;

	@Autowired
	private TransactionTemplate transactionTemplate;

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

	/**
	 * 이메일이 어디에도 없는 계정도 자기 정보를 읽을 수 있어야 한다 — S15P21E201-893.
	 *
	 * <p>애플은 그 사용자의 맨 처음 인증 때만 이메일을 주고, "이메일 숨기기" 를 고르거나 우리가 아직
	 * 이메일을 요청하지 않던 시절에 가입한 계정은 로컬 비밀번호 계정도 provider 이메일도 없다. 그런
	 * 계정에서 이 경로가 500 을 냈고, 앱은 로그인 직후 이 경로를 자동으로 부르기 때문에 애플 로그인이
	 * 서버까지 성공하고도 화면이 넘어가지 않았다.
	 *
	 * <p>그 상태를 실제 애플 호출 없이 만든다 — 가입으로 생긴 비밀번호 계정을 지우고, 이메일을 주지
	 * 않은 애플 연결만 남긴다. 판정에 쓰이는 조건(이메일 출처가 둘 다 빈다)은 운영에서 겪은 것과 같다.
	 */
	@Test
	void readsOwnProfileWhenNoEmailSourceRemains() {
		AuthedClient authed = loginAsNewUser("apple-without-email");
		ParameterizedTypeReference<ApiResponse<AuthUserResponse>> type =
				new ParameterizedTypeReference<ApiResponse<AuthUserResponse>>() {
				};

		UUID userId = authed.get("/api/v1/auth/me", type).getBody().data().userId();
		transactionTemplate.executeWithoutResult(status -> {
			LocalCredential credential = credentialRepository.findByUserUserId(userId).orElseThrow();
			identityRepository.save(AuthIdentity.link(credential.getUser(), AuthProvider.APPLE,
					"apple-subject-" + UUID.randomUUID(), null));
			credentialRepository.delete(credential);
		});

		ResponseEntity<ApiResponse<AuthUserResponse>> me = authed.get("/api/v1/auth/me", type);

		assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(me.getBody()).isNotNull();
		assertThat(me.getBody().data().userId()).isEqualTo(userId);
		assertThat(me.getBody().data().email()).isNull();
	}
}
