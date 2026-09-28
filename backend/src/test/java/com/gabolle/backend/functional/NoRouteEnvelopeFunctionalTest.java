package com.gabolle.backend.functional;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.functional.support.AuthedClient;
import com.gabolle.backend.functional.support.FunctionalJourneyTest;

/**
 * 없는 주소(404)·없는 메서드(405)도 우리 오류 봉투로 나간다 (S15P21E201-1675). 상태 코드는 그대로다.
 *
 * <p>진짜 포트로 띄운다 — 스프링이 이 둘을 기본 오류 본문으로 바꾸는 것은 서블릿 컨테이너가 {@code /error} 로 다시 넘길 때라
 * MockMvc 로는 못 잡는다. 고치기 전(2026-09-25 이 PC 로컬에서 잼): {@code {"timestamp","status":404,"error":"Not Found","path"}}.
 *
 * <p>🔴 로그인·oauth 경로와 기존 도메인 오류는 안 바뀐다 — 아래 「그대로」 시험들이 고치기 전에 잰 값을 그대로 못 박는다.
 * 하나만 바뀐다: 로그인 경로에 없는 메서드(GET)로 오면 전에는 {@code /error} 로 넘어가며 401 「로그인이 필요합니다」가 나갔는데,
 * 이제 405 봉투다 — 로그인 안 한 사람에게 로그인하라고 잘못 말하던 것이다.
 */
class NoRouteEnvelopeFunctionalTest extends FunctionalJourneyTest {

	private static final ParameterizedTypeReference<ApiResponse<Void>> ENVELOPE = new ParameterizedTypeReference<>() {
	};

	private static final String MESSAGE_KO = "지금은 이 기능을 쓸 수 없어요. 잠시 뒤 다시 시도해 주세요.";

	@Test
	@DisplayName("🔴 로그인한 사람이 없는 주소를 GET·POST 로 부르면 404 · 우리 봉투 · NOT_FOUND · 사용자 문장")
	void missingPathIsNotFoundInOurEnvelope() {
		AuthedClient user = loginAsNewUser("no-route");

		for (ResponseEntity<ApiResponse<Void>> response : java.util.List.of(
				user.get("/api/v1/이런경로는없다", ENVELOPE),
				user.get("/api/v1/jobs/" + UUID.randomUUID() + "/nope", ENVELOPE),
				user.post("/api/v1/이런경로는없다", java.util.Map.of(), ENVELOPE))) {
			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
			assertThat(response.getHeaders().getContentType()).isNotNull();
			assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
			assertThat(response.getBody().data()).isNull();
			assertThat(response.getBody().error().code()).isEqualTo("NOT_FOUND");
			assertThat(response.getBody().error().message()).isEqualTo(MESSAGE_KO);
			assertThat(response.getBody().meta().requestId()).isNotBlank();
		}
	}

	@Test
	@DisplayName("🔴 있는 주소에 없는 메서드 — 405 · METHOD_NOT_ALLOWED · 되는 메서드는 Allow 머리에 그대로")
	void wrongMethodIsMethodNotAllowedInOurEnvelope() {
		AuthedClient user = loginAsNewUser("no-route-method");

		// 여행 하나는 GET·DELETE 만 있다.
		ResponseEntity<ApiResponse<Void>> response = user.post("/api/v1/trips/" + UUID.randomUUID(), java.util.Map.of(),
				ENVELOPE);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
		assertThat(response.getHeaders().getAllow()).contains(HttpMethod.GET, HttpMethod.DELETE);
		assertThat(response.getBody().error().code()).isEqualTo("METHOD_NOT_ALLOWED");
		assertThat(response.getBody().error().message()).isEqualTo(MESSAGE_KO);
	}

	@Test
	@DisplayName("영어 사용자(Accept-Language: en)에게는 영어 문장 — 장소 상세와 같은 규칙(첫 언어)")
	void englishSpeakersGetAnEnglishSentence() {
		HttpHeaders headers = new HttpHeaders();
		headers.set(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9");

		// 로그인 경로는 POST 만 있고 로그인 없이 부를 수 있다 — GET 은 이제 405 봉투다(전에는 401).
		ResponseEntity<ApiResponse<Void>> response = this.rest.exchange("/api/v1/auth/login", HttpMethod.GET,
				new HttpEntity<>(headers), ENVELOPE);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
		assertThat(response.getHeaders().getAllow()).containsExactly(HttpMethod.POST);
		assertThat(response.getBody().error().code()).isEqualTo("METHOD_NOT_ALLOWED");
		assertThat(response.getBody().error().message())
				.isEqualTo("This feature isn't available right now. Please try again later.");
	}

	@Test
	@DisplayName("그대로 — 로그인 안 한 사람이 없는 주소를 부르면 전처럼 401 AUTHENTICATION_REQUIRED")
	void anonymousMissingPathIsStillUnauthenticated() {
		ResponseEntity<ApiResponse<Void>> response = this.rest.exchange("/api/v1/이런경로는없다", HttpMethod.GET,
				HttpEntity.EMPTY, ENVELOPE);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(response.getBody().error().code()).isEqualTo("AUTHENTICATION_REQUIRED");
	}

	@Test
	@DisplayName("그대로 — 있는 주소의 도메인 오류(없는 여행)는 전처럼 TRIP_NOT_FOUND")
	void domainErrorsAreUnchanged() {
		AuthedClient user = loginAsNewUser("no-route-domain");

		ResponseEntity<ApiResponse<Void>> response = user.get("/api/v1/trips/" + UUID.randomUUID(), ENVELOPE);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(response.getBody().error().code()).isEqualTo("TRIP_NOT_FOUND");
		assertThat(response.getBody().error().message()).isEqualTo("그 여행을 찾지 못했어요.");
	}

	@Test
	@DisplayName("🔴 그대로 — 로그인·oauth 경로는 404 가 아니라 전처럼 제 답을 한다")
	void loginAndOAuthRoutesAreUnchanged() {
		ResponseEntity<ApiResponse<Void>> login = post("/api/v1/auth/login",
				"{\"email\":\"nobody@example.com\",\"password\":\"wrong-password\"}");
		assertThat(login.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(login.getBody().error().code()).isEqualTo("INVALID_CREDENTIALS");

		for (String path : java.util.List.of("/api/v1/auth/oauth/unknownprovider", "/api/v1/auth/oauth/kakao/challenge",
				"/api/v1/auth/oauth/link")) {
			ResponseEntity<ApiResponse<Void>> oauth = post(path, "{}");
			assertThat(oauth.getStatusCode()).as(path).isEqualTo(HttpStatus.BAD_REQUEST);
			assertThat(oauth.getBody().error().code()).as(path).isEqualTo("INVALID_REQUEST");
		}

		// 애플은 결과를 폼으로 POST 한다 — 전처럼 화면으로 넘긴다.
		HttpHeaders form = new HttpHeaders();
		form.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
		ResponseEntity<String> apple = this.rest.exchange("/api/v1/auth/oauth/apple/form-post", HttpMethod.POST,
				new HttpEntity<>("code=x&state=y", form), String.class);
		assertThat(apple.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(apple.getBody()).doesNotContain("NOT_FOUND", "METHOD_NOT_ALLOWED");
	}

	private ResponseEntity<ApiResponse<Void>> post(String path, String json) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return this.rest.exchange(path, HttpMethod.POST, new HttpEntity<>(json, headers), ENVELOPE);
	}
}
