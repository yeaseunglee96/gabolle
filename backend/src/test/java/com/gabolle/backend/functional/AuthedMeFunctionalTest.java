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
 * 하네스가 실제로 도는지 증명하는 예시 시나리오. 회원가입 → 이메일 인증(DB 직접) → 로그인 → 인증이
 * 필요한 경로 호출까지 실제 소켓과 실제 {@code SecurityFilterChain} 을 통과시킨다.
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
	 * 이메일이 어디에도 없는 계정도 자기 정보를 읽을 수 있어야 한다. 애플은 맨 처음 인증 때만 이메일을
	 * 주므로, 로컬 비밀번호 계정도 provider 이메일도 없는 계정이 실제로 생긴다. 앱은 로그인 직후 이
	 * 경로를 자동으로 부르기 때문에 여기서 실패하면 로그인이 성공해도 화면이 안 넘어간다.
	 *
	 * <p>그 상태를 실제 애플 호출 없이 만든다 — 비밀번호 계정을 지우고 이메일 없는 애플 연결만 남긴다.
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
