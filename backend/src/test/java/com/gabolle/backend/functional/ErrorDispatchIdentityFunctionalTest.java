package com.gabolle.backend.functional;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.gabolle.backend.functional.support.AuthedClient;
import com.gabolle.backend.functional.support.FunctionalJourneyTest;

/**
 * 잘못된 요청이 401 로 바뀌지 않는다 — S15P21E201-790.
 *
 * <h2>무엇이 잘못돼 있었나</h2>
 * 서버가 4xx 를 정한 뒤 {@code /error} 로 다시 디스패치하는 것은 <b>같은 요청</b>인데,
 * 인증 필터는 요청 하나에 한 번만 도는 것이 기본값이라 그 두 번째 차례를 건너뛰었다. 그러면
 * 그 디스패치에는 신원이 없고, 인가가 거부해 <b>원래의 4xx 가 401 로 바뀌어</b> 나갔다.
 *
 * <p>거짓말을 하는 응답이라 나쁘다. 로그인은 멀쩡한데 "로그인이 필요합니다" 가 오고, 앱은
 * 401 을 세션 만료로 읽어 사용자를 로그아웃시킨다. 요청 하나가 잘못됐을 때 화면에서 튕겨
 * 나가는 모양이 된다.
 *
 * <p>이 검사는 실제 HTTP 로 그 자리를 재현한다. MockMvc 로는 이 재디스패치가 일어나지 않아
 * 원리상 잡을 수 없다 — 이 결함이 오래 안 보인 이유이기도 하다.
 */
class ErrorDispatchIdentityFunctionalTest extends FunctionalJourneyTest {

	private static final String TRIPS = "/api/v1/trips";

	/**
	 * 읽을 수 없는 몸통.
	 *
	 * <p>문자열을 그대로 넘기면 요청의 {@code Content-Type} 이 {@code text/plain} 이 되고, 서버는
	 * 그 요청을 415 로 거부한다. 그 거부가 {@code /error} 재디스패치를 거치면서 401 로 바뀌던 것이
	 * 이 티켓의 결함이다.
	 *
	 * <p>검증을 415 로 못 박지 않는다 — 이 검사가 지키는 것은 <b>"인증 실패로 보이지 않는다"</b>
	 * 이고, 서버가 나중에 400 으로 답하기로 해도 그 약속은 그대로다.
	 */
	private static final String UNREADABLE_BODY = "이건 JSON 이 아니다";

	@Test
	@DisplayName("완료 기준 — 로그인한 사람이 읽을 수 없는 몸통을 보내면 401 이 아니다")
	void anAuthenticatedRequestWithABadBodyIsNotReportedAsUnauthenticated() {
		AuthedClient authed = loginAsNewUser("error-dispatch");

		ResponseEntity<String> response = authed.post(TRIPS, UNREADABLE_BODY, String.class);

		assertThat(response.getStatusCode())
				.withFailMessage("잘못된 몸통인데 401 이 왔다 — 앱은 이것을 세션 만료로 읽는다: %s",
						response.getBody())
				.isNotEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(response.getStatusCode().is4xxClientError())
				.withFailMessage("잘못된 요청이니 4xx 여야 한다: %s", response.getStatusCode())
				.isTrue();
		assertThat(response.getBody()).doesNotContain("AUTHENTICATION_REQUIRED");
	}

	@Test
	@DisplayName("완료 기준 — 자격증명이 없으면 그대로 401 이다")
	void anAnonymousRequestIsStillUnauthenticated() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.TEXT_PLAIN);

		ResponseEntity<String> response = this.rest.exchange(TRIPS, HttpMethod.POST,
				new HttpEntity<>(UNREADABLE_BODY, headers), String.class);

		// 여기까지 바뀌면 인증이 헐거워진 것이다. 고친 것은 신원을 유지하는 것뿐이고,
		// 신원이 없는 요청의 거동은 그대로여야 한다.
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	@DisplayName("로그인한 사람이 없는 경로를 부르면 401 이 아니다")
	void anAuthenticatedRequestToAMissingPathIsNotFourZeroOne() {
		AuthedClient authed = loginAsNewUser("error-dispatch-missing");

		ResponseEntity<String> response = authed.get("/api/v1/이런경로는없다", String.class);

		assertThat(response.getStatusCode())
				.withFailMessage("없는 경로인데 401 이 왔다 — 앱은 이것을 세션 만료로 읽는다: %s",
						response.getBody())
				.isNotEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	@DisplayName("고친 뒤에도 인증이 필요한 경로는 자격증명 없이 못 부른다")
	void protectedRoutesStillRequireCredentials() {
		ResponseEntity<String> response = this.rest.getForEntity(TRIPS, String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(response.getBody()).contains("AUTHENTICATION_REQUIRED");
	}
}
