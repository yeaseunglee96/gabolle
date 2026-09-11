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
 * 애플의 {@code response_mode=form_post} 착지 — S15P21E201-833.
 *
 * <h2>왜 실제 HTTP 인가</h2>
 * 이 경로가 하는 일 자체는 리다이렉트 한 줄이고, 그 줄은 어떤 단위 검사로도 통과시킬 수 있다.
 * 정작 틀리기 쉬운 셋은 <b>전부 필터체인 위에 있다.</b>
 * <ul>
 *   <li><b>로그인 없이 열려 있는가</b> — 애플 서버가 부르므로 열려 있어야 한다. 허용 목록의
 *       {@code "/api/v1/auth/oauth/*"} 는 한 마디만 덮어 이 경로에 닿지 않는다. 2026-09-07 에
 *       같은 종류의 어긋남으로 소셜 로그인의 첫 요청이 401 이 났다(`-704`)</li>
 *   <li><b>폼 본문을 받는가</b> — 이 저장소의 나머지 인증 경로는 전부 JSON 이다</li>
 *   <li><b>리다이렉트가 실제로 나가는가</b>, 그리고 <b>어디로</b> 나가는가</li>
 * </ul>
 *
 * <h2>🔴 리다이렉트를 따라가지 않는 클라이언트를 직접 만든다</h2>
 * 처음에는 베이스 클래스의 {@code TestRestTemplate} 을 쓰고 "3xx 는 안 따라간다" 고 적어 뒀는데
 * <b>틀렸다.</b> CI 에서 이 검사가 빨개졌고, 응답 머리에 {@code Server: nginx}·CSP 가 실려 있었다 —
 * 즉 클라이언트가 302 를 따라가 <b>운영 도메인의 화면</b>을 받아 왔고, 그 200 을 보고 "리다이렉트가
 * 없다" 고 판정한 것이다. 테스트가 운영 서버로 나가는 것 자체도 이 검사가 재려던 것이 아니다.
 *
 * <p>그래서 JDK 의 {@code HttpClient} 를 {@code Redirect.NEVER} 로 직접 만들어 쓴다. 스프링
 * 컨텍스트를 건드리지 않으므로 베이스 클래스가 금지한 "여정마다 다른 프로퍼티" 에 해당하지 않는다.
 *
 * <h2>🔴 무엇을 재지 않는지</h2>
 * 애플이 실제로 이 주소에 POST 하는 것과, 그 뒤 코드 교환에서 서명된 토큰의 이메일이 계정에
 * 실리는 것은 <b>여기서 재지 않는다.</b> 애플 실계정과 개발자 콘솔의 Return URL 등록이 필요해
 * 자동 검사로 만들 수 없다. 그 둘은 사람이 한 번 눌러 확인해야 한다.
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
	 * 🔴 {@code Authorization} 머리를 <b>일부러 안 붙인다.</b> 이 경로가 로그인 없이 열려 있는지가
	 * 이 검사의 절반이다. 토큰을 붙이면 허용 목록에서 빠져 있어도 통과한다.
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
