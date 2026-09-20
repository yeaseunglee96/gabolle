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
 * 인가 정책 표가 말하는 것이 실제 서버에서도 그런지 본다. {@link RouteAuthorizationRegistryTest} 는
 * 빠뜨림을 재는 문서 대조라 서버를 띄우지 않고, 이쪽은 앱을 띄운 뒤 경로를 하나씩 실제로 두드린다.
 * MockMvc 로 컨트롤러만 부르는 검사는 보안 필터를 지나지 않아 이 종류의 실패를 원리상 못 잡는다.
 *
 * <p>경로 목록을 여기 또 적지 않는다 — 표에서 읽으므로 새 엔드포인트가 표에 적히면 자동으로
 * 두드린다.
 */
class RouteAuthorizationFunctionalTest extends FunctionalJourneyTest {

	/**
	 * 경로 변수 자리에 넣는 값. 실제로 존재하는 자원일 필요가 없다 — 보안 필터의 판정은 컨트롤러
	 * 앞에서 끝나므로 없는 자원이어도 401 이어야 하고, 404 가 온다면 인증을 안 보고 자원을 먼저
	 * 찾아봤다는 뜻이다.
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
	 * 열려 있어야 하는 경로가 보안 필터에 막히지 않는가. 401 인지로 판정하지 않는다 — 필터를 통과한
	 * 뒤 처리기가 자기 판단으로 내는 401 도 있기 때문이다. 대신 필터가 막았을 때만 실리는
	 * {@code AUTHENTICATION_REQUIRED} 가 오는 것만 실패로 센다.
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

	/** 필터가 막았을 때만 {@code AUTHENTICATION_REQUIRED} 가 실린다 — 처리기가 낸 401 은 다른 코드를 쓴다. */
	private static boolean blockedByTheFilter(ResponseEntity<String> response) {
		return response.getStatusCode() == HttpStatus.UNAUTHORIZED
				&& response.getBody() != null
				&& response.getBody().contains("AUTHENTICATION_REQUIRED");
	}

	/**
	 * {@code "POST /api/v1/trips/{}/members"} 한 줄을 실제 요청으로 바꿔 부른다. 몸통은 빈 JSON 이다 —
	 * 401 은 보안 필터가 몸통을 읽기 전에 내는 답이므로, 자격증명 없는 요청의 거부가 몸통에 달려
	 * 있으면 안 된다. 열린 경로에서 빈 몸통이 400 을 부르는 것은 상관없다.
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
