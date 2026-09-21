package com.gabolle.backend.functional;

import static org.assertj.core.api.Assertions.assertThat;

import com.gabolle.backend.functional.support.FunctionalJourneyTest;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 애플의 {@code response_mode=form_post} 착지. 이 경로가 하는 일은 리다이렉트 한 줄이지만 틀리기 쉬운
 * 셋이 전부 필터체인 위에 있다.
 * <ul>
 *   <li>로그인 없이 열려 있는가 — 애플 서버가 부르므로 열려 있어야 한다. 허용 목록의
 *       {@code "/api/v1/auth/oauth/*"} 는 한 마디만 덮어 이 경로에 닿지 않는다</li>
 *   <li>폼 본문을 받는가 — 이 저장소의 나머지 인증 경로는 전부 JSON 이다</li>
 *   <li>리다이렉트가 실제로, 어디로 나가는가</li>
 * </ul>
 *
 * <p>리다이렉트를 따라가지 않는 클라이언트를 직접 만든다. 베이스 클래스의 {@code TestRestTemplate} 은
 * 302 를 따라가 운영 도메인의 화면을 받아 오고, 그 200 을 보면 "리다이렉트가 없다" 로 잘못 판정된다.
 * JDK {@code HttpClient} 는 스프링 컨텍스트를 안 건드리므로 "여정마다 다른 프로퍼티" 에도 해당하지 않는다.
 *
 * <p>애플이 실제로 이 주소에 POST 하는 것과 그 뒤 코드 교환은 여기서 재지 않는다 — 애플 실계정과
 * 개발자 콘솔 등록이 필요해 사람이 한 번 눌러 확인해야 한다.
 */
class AppleFormPostJourneyFunctionalTest extends FunctionalJourneyTest {

	private static final String PATH = "/api/v1/auth/oauth/apple/form-post";

	/** 기본 설정값. 배포에서는 {@code GABOLLE_APPLE_FORM_POST_REDIRECT_URL} 이 덮는다. */
	private static final String EXPECTED_HOST = "j15e201.p.ssafy.io";

	@Test
	@DisplayName("애플이 보낸 폼을 로그인 없이 받고, code·state 를 화면 주소로 옮기는 302 를 낸다")
	void formPostIsOpenAndForwardsCodeAndState() throws Exception {
		HttpResponse<String> response = postForm(Map.of("code", "apple-auth-code-1", "state", "st-1"));

		assertThat(response.statusCode()).as("본문: %s", response.body()).isEqualTo(302);

		URI location = location(response);
		assertThat(location.getHost()).isEqualTo(EXPECTED_HOST);
		assertThat(location.getPath()).isEqualTo("/oauth/apple/callback");
		assertThat(location.getQuery()).contains("code=apple-auth-code-1").contains("state=st-1");
	}

	@Test
	@DisplayName("🔴 본문에 보낸 주소로는 절대 보내지 않는다 — 이 서버가 남의 주소로 사람을 보내 주는 도구가 되지 않는다")
	void bodySuppliedRedirectIsIgnored() throws Exception {
		Map<String, String> form = new LinkedHashMap<>();
		form.put("code", "apple-auth-code-2");
		form.put("state", "st-2");
		// 애플의 POST 는 서명 없는 폼 전송이라 누구나 흉내낼 수 있다. 그 본문이 목적지를 정할 수
		// 있으면, 이 경로 하나로 임의의 주소로 사람을 보낼 수 있다.
		form.put("redirect_uri", "https://evil.example.com/steal");
		form.put("redirectUrl", "https://evil.example.com/steal");

		HttpResponse<String> response = postForm(form);

		URI location = location(response);
		assertThat(location.getHost()).as("본문이 준 주소로 갔다 — open redirect").isEqualTo(EXPECTED_HOST);
		assertThat(location.toString()).doesNotContain("evil.example.com");
	}

	@Test
	@DisplayName("애플이 거절하면 그 오류 코드를 화면으로 넘긴다 — 화면이 취소와 실패를 가려 말할 수 있게")
	void errorIsForwarded() throws Exception {
		HttpResponse<String> response = postForm(Map.of("error", "user_cancelled_authorize"));

		assertThat(location(response).getQuery()).contains("error=user_cancelled_authorize");
	}

	@Test
	@DisplayName("빈 폼도 화면으로 보낸다 — 브라우저에 JSON 오류를 보여 주지 않는다")
	void emptyFormStillRedirects() throws Exception {
		HttpResponse<String> response = postForm(Map.of());

		assertThat(response.statusCode()).isEqualTo(302);
		assertThat(location(response).getQuery()).contains("error=APPLE_FORM_POST_EMPTY");
	}

	@Test
	@DisplayName("🔴 폼이 아닌 몸통으로 와도 401 이 아니다 — 인가 감사가 빈 JSON 으로 이 경로를 찔러 본다")
	void nonFormBodyIsStillReachable() throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create(baseUri() + PATH))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString("{}"))
				.build();

		HttpResponse<String> response = client().send(request, HttpResponse.BodyHandlers.ofString());

		assertThat(response.statusCode()).as("본문: %s", response.body()).isNotEqualTo(401);
		assertThat(response.body()).doesNotContain("AUTHENTICATION_REQUIRED");
	}

	private URI location(HttpResponse<String> response) {
		Optional<String> header = response.headers().firstValue("Location");
		assertThat(header).as("Location 이 없다 — 리다이렉트가 안 나갔다. 상태=%s", response.statusCode()).isPresent();
		return URI.create(header.orElseThrow());
	}

	/**
	 * {@code Authorization} 머리를 일부러 안 붙인다. 붙이면 허용 목록에서 빠져 있어도 통과해서, 이
	 * 경로가 로그인 없이 열려 있는지를 못 잰다.
	 */
	private HttpResponse<String> postForm(Map<String, String> form) throws IOException, InterruptedException {
		StringJoiner body = new StringJoiner("&");
		form.forEach((key, value) -> body.add(java.net.URLEncoder.encode(key, java.nio.charset.StandardCharsets.UTF_8)
				+ "=" + java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8)));

		HttpRequest request = HttpRequest.newBuilder(URI.create(baseUri() + PATH))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(body.toString()))
				.build();
		return client().send(request, HttpResponse.BodyHandlers.ofString());
	}

	private static HttpClient client() {
		return HttpClient.newBuilder()
				.followRedirects(HttpClient.Redirect.NEVER)
				.connectTimeout(Duration.ofSeconds(5))
				.build();
	}

	/** 베이스 클래스가 띄운 앱의 주소. 무작위 포트라 여기서 물어봐야 한다. */
	private String baseUri() {
		String rootUri = this.rest.getRootUri();
		return rootUri.endsWith("/") ? rootUri.substring(0, rootUri.length() - 1) : rootUri;
	}
}
