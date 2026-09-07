package com.gabolle.backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code SecurityConfig} 의 허용 목록과 실제 경로가 <b>양방향으로</b> 맞는지 본다 — `-672`·`-704`.
 *
 * <h2>이 파일이 막는 두 가지</h2>
 * 허용 목록과 컨트롤러 매핑은 <b>서로 다른 두 파일에 나뉘어 있지만 하나의 사실</b>이다. 한쪽만
 * 보고 다른 쪽을 바꾸면 git 은 서로 다른 줄이라 충돌 없이 합치고, 결과만 틀어진다. 그 어긋남이
 * 두 방향으로 생긴다.
 *
 * <p><b>목록에는 있는데 경로가 없다.</b> 지금은 뚫을 것이 없어 무해하다. 위험은 나중이다 —
 * 몇 달 뒤 누가 그 경로로 컨트롤러를 만들면 인증을 켤지 끌지 <b>결정한 적도 없이 첫 커밋부터
 * 열린 상태</b>가 되고, 그 MR 은 이 파일을 건드리지 않으므로 리뷰에도 안 보인다.
 * {@link #allowlistOnlyOpensExistingRoutes} 가 이쪽이다.
 *
 * <p>🔴 <b>경로는 있는데 목록에 없다.</b> 이쪽은 <b>지금 당장 기능이 죽는다.</b> 2026-09-07
 * 저녁에 실제로 그랬다 — 한 작업이 {@code AuthController} 에 소셜 챌린지 매핑을 되살리고
 * (`-704`), 다른 작업이 같은 시간에 "그 경로를 매핑하는 컨트롤러가 없다" 고 판단해 이 목록에서
 * 그 줄을 지웠다(`-672`). 두 판단은 각자의 브랜치에서 옳았다. 차례로 머지되자 <b>경로는 있는데
 * 인증을 요구하는 상태</b>가 됐고, 챌린지 발급이 소셜 로그인의 첫 요청이라 브라우저가 열리기도
 * 전에 401 이 났다. 앱은 401 을 전부 비밀번호 오류 문구로 바꿔 보여주므로, 사용자에게는 "소셜
 * 버튼을 눌렀는데 아이디·비밀번호가 틀렸다고 한다" 로 보였다.
 * {@link #preAuthRoutesAreActuallyOpen} 이 이쪽이고, 이 사고 뒤에 더했다.
 *
 * <h2>와일드카드를 어떻게 다루는가</h2>
 * 목록의 {@code *} 는 <b>어떤 한 마디든</b> 덮는다. 그래서 {@code "/api/v1/auth/oauth/*"} 는
 * {@code /oauth/{provider}} 뿐 아니라 {@code /oauth/signup}·{@code /oauth/link} 까지 연다 —
 * {@code SecurityConfig} 주석이 경고해 둔 그 동작이다. 마디 수가 다르면 안 덮는다는 것도 같은
 * 규칙에서 나온다({@code /oauth/*} 는 {@code /oauth/{}/challenge} 를 안 덮는다).
 *
 * <p>"목록에 있는데 경로가 없다" 쪽은 <b>고정 경로만</b> 본다. 와일드카드 항목은 실제로 존재하지
 * 않는 경로까지 덮는 것처럼 보일 수 있어 거짓 실패가 나기 쉽다. 그쪽은
 * {@link RouteAuthorizationRegistryTest} 의 "열린 경로 개수 고정" 이 함께 지킨다.
 */
class SecurityAllowlistMatchesRoutesTest {

	private static final Path SECURITY_CONFIG = Path.of(
			"src/main/java/com/gabolle/backend/auth/config/SecurityConfig.java");

	/**
	 * 허용 목록에 있지만 대응하는 엔드포인트가 <b>지금은</b> 없어도 되는 것.
	 *
	 * <p>비워 두는 것이 정답이다. 여기에 줄이 늘어나는 것은 "쓰지 않는 문을 열어 두고 있다" 는
	 * 뜻이므로, 넣을 때는 근거와 함께 넣고 왜 지금 못 지우는지 적는다.
	 */
	private static final Set<String> KNOWN_ABSENT = Set.of();

	@Test
	@DisplayName("🔴 허용 목록의 고정 경로가 모두 실제로 존재한다 — 없는 문을 미리 열어 두지 않는다")
	void allowlistOnlyOpensExistingRoutes() throws IOException {
		Set<String> allowed = literalAllowlistPaths();
		Set<String> actual = routePathsOnly();

		Set<String> absent = new TreeSet<>(allowed);
		absent.removeAll(actual);
		absent.removeAll(KNOWN_ABSENT);

		assertThat(absent)
				.withFailMessage("""
						SecurityConfig 가 인증 없이 열어 둔 경로 중 실제로 존재하지 않는 것이 있습니다.

						%s

						지금은 뚫을 것이 없어 무해하지만, 나중에 누가 이 경로로 컨트롤러를 만들면
						인증 여부를 결정한 적도 없이 처음부터 열린 상태가 됩니다. 그 MR 은
						SecurityConfig 를 건드리지 않으므로 리뷰에서도 안 보입니다.

						쓰지 않는 줄이면 SecurityConfig 에서 지우고, 곧 만들 예정이면 이 파일의
						KNOWN_ABSENT 에 근거와 함께 적어 주세요.""".formatted(String.join("\n", absent)))
				.isEmpty();
	}

	@Test
	@DisplayName("🔴 로그인 전에 부르는 경로가 허용 목록에서 빠지지 않았다 — 빠지면 그 기능이 아예 막힌다")
	void preAuthRoutesAreActuallyOpen() throws IOException {
		String source = Files.readString(SECURITY_CONFIG, StandardCharsets.UTF_8);

		List<String> closed = new ArrayList<>();
		for (String route : RouteAuthorizationRegistryTest.preAuthRoutesForAudit()) {
			String path = route.substring(route.indexOf(' ') + 1);
			if (!allowlistCovers(source, path)) {
				closed.add(route);
			}
		}

		assertThat(closed)
				.withFailMessage("""
						정책 표에서 PRE_AUTH(로그인 전에 부르는 경로)로 분류했는데 SecurityConfig 의
						허용 목록에 없습니다. 이대로 배포하면 그 기능이 <b>첫 요청부터 401</b> 입니다.

						%s

						🔴 2026-09-07 에 실제로 그렇게 소셜 로그인이 막혔습니다(-704). 한 작업이
						AuthController 에 챌린지 매핑을 되살리고, 다른 작업이 같은 시간에 이 목록에서
						그 줄을 지웠습니다. 서로 다른 파일의 서로 다른 줄이라 git 은 충돌 없이 합쳤고,
						결과는 "경로는 있는데 인증을 요구하는 상태" 였습니다.

						허용 목록에 그 경로를 넣거나, 로그인 전에 부르는 것이 아니라면 정책 표의
						분류를 고쳐 주세요.""".formatted(String.join(System.lineSeparator(), closed)))
				.isEmpty();
	}

	/**
	 * 허용 목록의 어느 항목이 이 경로를 덮는가.
	 *
	 * <p>와일드카드를 한 마디 대치로 다룬다 — {@code "/api/v1/auth/oauth/*"} 는
	 * {@code /api/v1/auth/oauth/{}} 를 덮지만 {@code /api/v1/auth/oauth/{}/challenge} 는
	 * 덮지 않는다. 이 규칙이 이 저장소에서 실제로 문제가 된 자리다 —
	 * {@code SecurityConfig} 주석이 "두 마디로 두면 조용히 열리지 않는다" 고 적어 두고 있다.
	 */
	private static boolean allowlistCovers(String source, String path) {
		String[] wanted = path.split("/");
		Matcher matcher = Pattern.compile("\"(/api/[^\"]*)\"").matcher(source);
		while (matcher.find()) {
			String[] entry = matcher.group(1).split("/");
			if (entry.length != wanted.length) {
				continue;
			}
			boolean all = true;
			for (int i = 0; i < entry.length; i++) {
				// 🔴 허용 목록의 * 는 <b>어떤 한 마디든</b> 덮는다 — 경로 변수 자리만이 아니다.
				//    "/oauth/*" 가 /oauth/{provider} 뿐 아니라 /oauth/signup · /oauth/link 까지
				//    여는 것이 이 저장소가 주석으로 경고해 둔 바로 그 동작이다.
				boolean wild = entry[i].equals("*");
				if (!wild && !entry[i].equals(wanted[i])) {
					all = false;
					break;
				}
			}
			if (all) {
				return true;
			}
		}
		return false;
	}

	@Test
	@DisplayName("허용 목록을 실제로 읽었다 — 정규식이 아무것도 못 잡으면 이 검사가 무의미하다")
	void allowlistWasActuallyParsed() throws IOException {
		// 🔴 파싱이 실패해 빈 집합이 되면 위 확인이 "없는 경로가 없다" 며 초록이 된다.
		//    있는 것만 세는 검사가 없는 것을 못 잡는 것과 같은 함정이라 여기서 하한을 박는다.
		//    개수를 정확히 박지 않는 이유는 허용 목록이 정당하게 바뀔 때마다 이 줄이 빨개지면
		//    사람이 숫자만 고치고 넘어가게 되기 때문이다 — 대신 반드시 있어야 하는 항목 하나를
		//    이름으로 확인한다. 로그인이 목록에서 사라지는 일은 없다.
		assertThat(literalAllowlistPaths())
				.hasSizeGreaterThanOrEqualTo(5)
				.contains("/api/v1/auth/login");
	}

	/** {@code SecurityConfig} 의 문자열 중 경로 변수·와일드카드가 없는 {@code /api/...} 만. */
	private static Set<String> literalAllowlistPaths() throws IOException {
		String source = Files.readString(SECURITY_CONFIG, StandardCharsets.UTF_8);
		// 주석 안의 예시 경로를 세지 않도록 줄 단위로 주석을 먼저 걷어낸다
		StringBuilder code = new StringBuilder();
		for (String line : source.split("\n")) {
			String trimmed = line.trim();
			if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
				continue;
			}
			code.append(line).append('\n');
		}

		Set<String> paths = new LinkedHashSet<>();
		Matcher matcher = Pattern.compile("\"(/api/[^\"]*)\"").matcher(code);
		while (matcher.find()) {
			String path = matcher.group(1);
			if (path.contains("*") || path.contains("{")) {
				continue;
			}
			paths.add(path);
		}
		return paths;
	}

	/** {@link RouteAuthorizationRegistryTest} 가 찾은 경로에서 동사를 떼고 경로만. */
	private static Set<String> routePathsOnly() {
		Set<String> paths = new TreeSet<>();
		for (String route : RouteAuthorizationRegistryTest.discoverRoutesForAudit()) {
			paths.add(route.substring(route.indexOf(' ') + 1));
		}
		return paths;
	}
}
