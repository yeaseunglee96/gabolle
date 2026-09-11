package com.gabolle.backend.functional;

import static org.assertj.core.api.Assertions.assertThat;

import com.gabolle.backend.functional.support.FunctionalJourneyTest;
import java.net.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * 애플의 {@code response_mode=form_post} 착지 — S15P21E201-833.
 *
 * <h2>왜 실제 HTTP 인가</h2>
 * 이 경로가 하는 일 자체는 리다이렉트 한 줄이고, 그 줄은 어떤 단위 검사로도 통과시킬 수 있다.
 * 정작 이 티켓에서 틀리기 쉬운 셋은 <b>전부 필터체인 위에 있다.</b>
 * <ul>
 *   <li><b>로그인 없이 열려 있는가</b> — 애플 서버가 부르므로 열려 있어야 한다. 허용 목록의
 *       {@code "/api/v1/auth/oauth/*"} 는 한 마디만 덮어 이 경로에 닿지 않는다. 2026-09-07 에
 *       같은 종류의 어긋남으로 소셜 로그인의 첫 요청이 401 이 났다(`-704`)</li>
 *   <li><b>폼 본문을 받는가</b> — 이 저장소의 나머지 인증 경로는 전부 JSON 이다</li>
 *   <li><b>리다이렉트가 실제로 나가는가</b> — {@code TestRestTemplate} 은 3xx 를 따라가지 않게
 *       두고 {@code Location} 머리를 직접 본다. 따라가면 화면(SPA)이 200 을 주고, 그러면 이
 *       검사는 리다이렉트가 없어도 초록이 된다</li>
 * </ul>
 *
 * <h2>🔴 무엇을 재지 않는지</h2>
 * 애플이 실제로 이 주소에 POST 하는 것과, 그 뒤 코드 교환에서 서명된 토큰의 이메일이 계정에
 * 실리는 것은 <b>여기서 재지 않는다.</b> 애플 실계정과 개발자 콘솔의 Return URL 등록이 필요해
 * 자동 검사로 만들 수 없다. 그 두 개는 사람이 한 번 눌러 확인해야 한다 — 티켓의 경계 절에 적었다.
 */
class AppleFormPostJourneyFunctionalTest extends FunctionalJourneyTest {

	private static final String PATH = "/api/v1/auth/oauth/apple/form-post";

	/** 기본 설정값. 배포에서는 {@code GABOLLE_APPLE_FORM_POST_REDIRECT_URL} 이 덮는다. */
	private static final String EXPECTED_HOST = "j15e201.p.ssafy.io";

	@Test
	@DisplayName("애플이 보낸 폼을 로그인 없이 받고, code·state 를 화면 주소로 옮기는 302 를 낸다")
	void formPostIsOpenAndForwardsCodeAndState() {
		ResponseEntity<Void> response = postForm(form("code", "apple-auth-code-1", "state", "st-1"));

		assertThat(response.getStatusCode()).as("응답 머리: %s", response.getHeaders()).isEqualTo(HttpStatus.FOUND);

		URI location = response.getHeaders().getLocation();
		assertThat(location).as("Location 이 없다 — 리다이렉트가 안 나갔다").isNotNull();
		assertThat(location.getHost()).isEqualTo(EXPECTED_HOST);
		assertThat(location.getPath()).isEqualTo("/oauth/apple/callback");
		assertThat(location.getQuery()).contains("code=apple-auth-code-1").contains("state=st-1");
	}

	@Test
	@DisplayName("🔴 본문에 보낸 주소로는 절대 보내지 않는다 — 이 서버가 남의 주소로 사람을 보내 주는 도구가 되지 않는다")
	void bodySuppliedRedirectIsIgnored() {
		ResponseEntity<Void> response = postForm(form(
				"code", "apple-auth-code-2",
				"state", "st-2",
				// 애플의 POST 는 서명 없는 폼 전송이라 누구나 흉내낼 수 있다. 그 본문이 목적지를
				// 정할 수 있으면, 이 경로 하나로 임의의 주소로 사람을 보낼 수 있다.
				"redirect_uri", "https://evil.example.com/steal",
				"redirectUrl", "https://evil.example.com/steal"));

		URI location = response.getHeaders().getLocation();
		assertThat(location).isNotNull();
		assertThat(location.getHost()).as("본문이 준 주소로 갔다 — open redirect").isEqualTo(EXPECTED_HOST);
		assertThat(location.toString()).doesNotContain("evil.example.com");
	}

	@Test
	@DisplayName("애플이 거절하면 그 오류 코드를 화면으로 넘긴다 — 화면이 취소와 실패를 가려 말할 수 있게")
	void errorIsForwarded() {
		ResponseEntity<Void> response = postForm(form("error", "user_cancelled_authorize"));

		URI location = response.getHeaders().getLocation();
		assertThat(location).isNotNull();
		assertThat(location.getQuery()).contains("error=user_cancelled_authorize");
	}

	@Test
	@DisplayName("빈 폼도 화면으로 보낸다 — 브라우저에 JSON 오류를 보여 주지 않는다")
	void emptyFormStillRedirects() {
		ResponseEntity<Void> response = postForm(new LinkedMultiValueMap<>());

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
		URI location = response.getHeaders().getLocation();
		assertThat(location).isNotNull();
		assertThat(location.getQuery()).contains("error=APPLE_FORM_POST_EMPTY");
	}

	private static MultiValueMap<String, String> form(String... keyValues) {
		MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
		for (int i = 0; i < keyValues.length; i += 2) {
			body.add(keyValues[i], keyValues[i + 1]);
		}
		return body;
	}

	/**
	 * 🔴 {@code Authorization} 머리를 <b>일부러 안 붙인다.</b> 이 경로가 로그인 없이 열려 있는지가
	 * 이 검사의 절반이다. 토큰을 붙이면 허용 목록에서 빠져 있어도 통과한다.
	 */
	private ResponseEntity<Void> postForm(MultiValueMap<String, String> body) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
		return this.rest.exchange(PATH, HttpMethod.POST, new HttpEntity<>(body, headers), Void.class);
	}
}
