package com.gabolle.backend.functional;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.gabolle.backend.functional.support.FunctionalJourneyTest;

/**
 * 회원가입 본문이 실제 HTTP 에서 읽히는가 — S15P21E201-811.
 *
 * <h2>왜 이 검사가 따로 있나</h2>
 * {@code FunctionalJourneyTest.loginAsNewUser} 는 가입 본문을 <b>객체로</b> 보낸다. 그러면
 * 보내는 쪽 Jackson 이 만든 JSON 만 검증되고, <b>손으로 쓴 JSON 이 읽히는가</b> 는 안 재진다.
 * 실제 앱과 팀의 다른 도구는 손으로 만든 JSON 을 보낸다.
 *
 * <p>2026-09-10 운영에서 가입이 전부 {@code 400 INVALID_REQUEST} 로 실패했다. 응답의
 * {@code fields} 가 비어 있었는데, 그것은 검증에 걸린 것이 아니라 <b>본문을 아예 못 읽었다</b>는
 * 뜻이다(검증 실패면 어느 칸이 틀렸는지가 들어온다). 같은 시각 로그인은 같은 방식으로 보낸
 * 빈 본문에 칸 이름을 제대로 돌려줬다 — 즉 JSON 처리 자체는 살아 있었다.
 *
 * <h2>비어 있는 본문으로 재는 이유</h2>
 * {@code {}} 를 보내면 <b>읽기</b>와 <b>검증</b>이 갈린다. 읽히면 검증이 돌아 어느 칸이 비었는지
 * 알려 주고, 못 읽으면 그 목록이 빈 채로 온다. 유효한 본문으로 재면 둘을 구분할 수 없다.
 */
class SignupContractFunctionalTest extends FunctionalJourneyTest {

	private ResponseEntity<String> postJson(String path, String body) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
	}

	@Test
	@DisplayName("빈 본문을 보내면 어느 칸이 비었는지 돌려준다 — 본문을 못 읽으면 그 목록이 빈다")
	void anEmptyBodyIsReadAndReportedFieldByField() {
		ResponseEntity<String> response = postJson("/api/v1/auth/signup", "{}");

		assertThat(response.getStatusCode().value()).isEqualTo(400);
		assertThat(response.getBody())
				.as("본문을 못 읽으면 fields 가 빈 채로 온다. 그러면 가입이 어떤 값으로도 성공하지 않는다. 응답 전문: %s",
						response.getBody())
				.contains("email")
				.contains("password");
	}

	@Test
	@DisplayName("나이 확인을 빼면 그 문구가 그대로 나간다 — 형식 오류로 뭉개지지 않는다")
	void theAgeGateMessageReachesTheCaller() {
		String body = """
				{"email":"age-gate@example.com","password":"correct-horse-battery-staple",				"displayName":"age gate","language":"KO"}""";

		ResponseEntity<String> response = postJson("/api/v1/auth/signup", body);

		assertThat(response.getStatusCode().value()).isEqualTo(400);
		assertThat(response.getBody())
				.as("나이 확인 문구가 응답에 없다. 응답 전문: %s", response.getBody())
				.contains("14세 이상 확인이 필요합니다");
	}

	@Test
	@DisplayName("로그아웃도 기기 범위를 빼고 보낼 수 있다 — 한 기기만 끊는 것이 보통이다")
	void logoutAcceptsABodyWithoutTheDeviceScope() {
		ResponseEntity<String> response = postJson("/api/v1/auth/logout", "{\"refreshToken\":\"not-a-real-token\"}");

		// 토큰이 가짜라 성공하지는 않는다. 재는 것은 본문을 읽었는가다 —
		// 못 읽으면 400 INVALID_REQUEST 로 끝나고 토큰 검사까지 가지도 못한다.
		assertThat(response.getBody())
				.as("본문을 못 읽었다. 응답 전문: %s", response.getBody())
				.doesNotContain("요청 형식이 올바르지 않습니다");
	}

	@Test
	@DisplayName("손으로 쓴 JSON 으로도 가입이 된다 — 객체로 보낼 때만 되는 것이 아니다")
	void aHandWrittenBodyCreatesAnAccount() {
		String email = "signup-contract+" + java.util.UUID.randomUUID() + "@example.com";
		String body = """
				{"email":"%s","password":"correct-horse-battery-staple","displayName":"계약검사",\
				"language":"KO","ageGateAccepted":true,"deviceId":"contract-test",\
				"consents":{"TERMS_OF_SERVICE":true,"PRIVACY_POLICY":true},\
				"behaviorPersonalizationEnabled":false}""".formatted(email);

		ResponseEntity<String> response = postJson("/api/v1/auth/signup", body);

		assertThat(response.getStatusCode().value())
				.as("가입이 실패했다. 응답 전문: %s", response.getBody())
				.isEqualTo(201);
	}
}
