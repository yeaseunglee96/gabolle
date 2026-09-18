package com.gabolle.backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.gabolle.backend.functional.support.FunctionalJourneyTest;

/**
 * 인가 정책 표가 말하는 것이 <b>실제 서버에서도 그런가</b> — S15P21E201-788.
 *
 * <h2>옆에 있는 검사와 무엇이 다른가</h2>
 * {@link RouteAuthorizationRegistryTest} 는 <b>빠뜨림</b>을 잰다 — 어느 경로도 정책 없이
 * 존재하지 않는지. 그건 문서 대조이고 서버를 띄우지 않는다. 이 검사는 그 표를 <b>데이터로
 * 읽어</b> 앱을 한 번 띄운 뒤 경로를 하나씩 실제로 두드린다. 즉 표가 "이건 로그인이 필요하다"
 * 고 적어 둔 경로가 정말로 거부하는지를 본다.
 *
 * <p>이 간극이 실제로 사고를 냈다. 2026-09-07 저녁에 경로 복구와 허용 목록 정리가 각자 옳았지만
 * 합쳐지자 "경로는 있는데 로그인 전 요청이 막히는" 상태가 됐고, 소셜 로그인 첫 요청이 통째로
 * 죽었다(`S15P21E201-704`). MockMvc 로 컨트롤러만 부르는 검사는 보안 필터를 지나지 않으므로
 * 이 종류의 실패를 <b>원리상 못 잡는다.</b>
 *
 * <h2>손으로 목록을 적지 않는다</h2>
 * 경로 목록은 표에서 온다. 새 엔드포인트를 만들면 표에 적어야 빌드가 통과하고
 * ({@code RouteAuthorizationRegistryTest}), 표에 적히면 이 검사가 자동으로 그 경로도 두드린다.
 * 목록을 여기 또 적으면 언젠가 한쪽만 늘어난다.
 */
class RouteAuthorizationFunctionalTest extends FunctionalJourneyTest {

	/**
	 * 경로 변수 자리에 넣는 값. <b>실제로 존재하는 자원일 필요가 없다.</b>
	 *
	 * <p>이 검사가 보는 것은 <b>보안 필터가 요청을 통과시키는가</b>이고, 그 판정은 자원이
	 * 있는지와 무관하게 컨트롤러 앞에서 끝난다. 로그인이 필요한 경로라면 없는 자원이어도
	 * 401 이어야 한다 — 404 가 온다면 그건 <b>인증을 안 보고 자원을 먼저 찾아봤다</b>는 뜻이다.
	 */
	private static String someIdentifier() {
		return UUID.randomUUID().toString();
	}

	@Test
	@DisplayName("표가 로그인을 요구한다고 적어 둔 경로는 자격증명 없이 부르면 전부 401 이다")
	void protectedRoutesRejectAnonymousCalls() {
		Set<String> protectedRoutes = new TreeSet<>(RouteAuthorizationRegistryTest.discoverRoutesForAudit());
		protectedRoutes.removeAll(RouteAuthorizationRegistryTest.openWithoutLoginRoutesForAudit());

		// 표에 경로가 있어야 이 검사가 의미를 갖는다. 0개면 아무것도 안 두드리고 초록이 된다.
		assertThat(protectedRoutes).isNotEmpty();

		List<String> leaks = new ArrayList<>();
		for (String route : protectedRoutes) {
			ResponseEntity<String> response = callAnonymously(route);
			if (response.getStatusCode() != HttpStatus.UNAUTHORIZED) {
				leaks.add("%s → %s (401 이어야 한다)".formatted(route, response.getStatusCode()));
			}
		}

		assertThat(leaks)
				.withFailMessage("""
						자격증명 없이 부르는데 401 이 아닌 경로가 있습니다. 표에는 로그인이 필요하다고
						적혀 있는데 서버가 다르게 답합니다 — 허용 목록(SecurityConfig)과 표 중 하나가
						틀렸습니다.

						%s""".formatted(String.join("\n", leaks)))
				.isEmpty();
	}

	/**
	 * 열려 있어야 하는 경로가 <b>보안 필터에</b> 막히지 않는가.
	 *
	 * <h3>🔴 401 인지로 판정하지 않는다 — 그것으로는 두 가지가 구분되지 않는다</h3>
	 * 처음에는 "열린 경로는 401 이 아니다" 로 단정했고 {@code POST /api/v1/auth/web/refresh} 가
	 * 걸렸다. 확인해 보니 결함이 아니었다 — 그 경로는 <b>필터를 통과한 뒤 처리기가</b> "refresh
	 * 쿠키가 없다"({@code INVALID_REFRESH_TOKEN})로 401 을 답한다. 자기 일을 한 것이다.
	 *
	 * <p>그래서 상태 코드 대신 <b>누가 거절했는가</b>를 본다. 필터가 막으면 응답에
	 * {@code AUTHENTICATION_REQUIRED} 가 실린다({@code ApiAuthenticationEntryPoint}). 그 코드가
	 * 오는 것만 실패로 센다 — S15P21E201-704 의 사고가 정확히 그 모양이었고, 처리기가 자기
	 * 판단으로 내는 401 은 이 검사가 잡을 것이 아니다.
	 */
	@Test
	@DisplayName("표가 로그인 전에 열려 있다고 적어 둔 경로는 보안 필터에 막히지 않는다")
	void openRoutesAreReachableWithoutLogin() {
		Set<String> open = new TreeSet<>(RouteAuthorizationRegistryTest.openWithoutLoginRoutesForAudit());
		assertThat(open).isNotEmpty();

		List<String> blocked = new ArrayList<>();
		for (String route : open) {
			ResponseEntity<String> response = callAnonymously(route);
			if (blockedByTheFilter(response)) {
				blocked.add("%s → %s %s".formatted(route, response.getStatusCode(), response.getBody()));
			}
		}

		assertThat(blocked)
				.withFailMessage("""
						로그인 전에 열려 있어야 하는 경로를 보안 필터가 막고 있습니다. 이 상태에서는
						회원가입·로그인·소셜 로그인 첫 요청이 막혀 아무도 들어올 수 없습니다 —
						S15P21E201-704 가 정확히 이 모양이었습니다.

						%s""".formatted(String.join("\n", blocked)))
				.isEmpty();
	}

	/**
	 * 이 응답이 <b>보안 필터의</b> 거절인가.
	 *
	 * <p>필터가 막았을 때만 {@code AUTHENTICATION_REQUIRED} 가 실린다. 같은 401 이라도 처리기가
	 * 자기 이유로 낸 것은 다른 코드를 쓴다.
	 */
	private static boolean blockedByTheFilter(ResponseEntity<String> response) {
		return response.getStatusCode() == HttpStatus.UNAUTHORIZED
				&& response.getBody() != null
				&& response.getBody().contains("AUTHENTICATION_REQUIRED");
	}

	/**
	 * {@code "POST /api/v1/trips/{}/members"} 한 줄을 실제 요청으로 바꿔 부른다.
	 *
	 * <p>몸통은 빈 JSON 이다. <b>자격증명 없는 요청의 거부가 몸통에 달려 있으면 안 된다</b> —
	 * 401 은 보안 필터가 내는 답이고 그 자리에서는 아직 몸통을 읽지도 않았다. 그래서 몸통을
	 * 정교하게 만들 이유가 없고, 만들면 오히려 검사가 무엇을 재는지 흐려진다.
	 *
	 * <p>열린 경로에서는 이 빈 몸통이 400 을 부른다. 그것으로 충분하다 — 이 검사는 열린 경로가
	 * 무엇을 <b>해내는지</b>가 아니라 401 로 막히지 않는지만 본다.
	 */
	private ResponseEntity<String> callAnonymously(String route) {
		int space = route.indexOf(' ');
		HttpMethod method = HttpMethod.valueOf(route.substring(0, space));
		String path = route.substring(space + 1).replace("{}", someIdentifier());

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		HttpEntity<String> request = new HttpEntity<>("{}", headers);

		return this.rest.exchange(path, method, request, String.class);
	}
}
