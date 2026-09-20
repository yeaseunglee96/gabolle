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
 * {@code SecurityConfig} 의 허용 목록과 실제 경로가 양방향으로 맞는지 본다. 둘은 서로 다른 파일에
 * 나뉘어 있지만 하나의 사실이라, 한쪽만 고치면 git 이 충돌 없이 합치고 결과만 틀어진다.
 *
 * <p>{@link #allowlistOnlyOpensExistingRoutes} 는 목록에는 있는데 경로가 없는 쪽을 본다. 지금은
 * 무해하지만 나중에 누가 그 경로로 컨트롤러를 만들면 결정한 적 없이 첫 커밋부터 열린 상태가 되고,
 * 그 MR 은 이 파일을 건드리지 않아 리뷰에도 안 보인다.
 *
 * <p>{@link #preAuthRoutesAreActuallyOpen} 은 경로는 있는데 목록에 없는 쪽이다. 이쪽은 그 기능이
 * 첫 요청부터 401 이라 지금 당장 죽는다.
 *
 * <p>목록의 {@code *} 는 어떤 한 마디든 덮는다 — {@code "/api/v1/auth/oauth/*"} 는
 * {@code /oauth/{provider}} 뿐 아니라 {@code /oauth/signup} 까지 연다. 마디 수가 다르면 안 덮는다.
 *
 * <p>"목록에 있는데 경로가 없다" 쪽은 고정 경로만 본다. 와일드카드 항목은 거짓 실패가 나기 쉬워
 * {@link RouteAuthorizationRegistryTest} 의 열린 경로 개수 고정이 대신 지킨다.
 */
class SecurityAllowlistMatchesRoutesTest {

	private static final Path SECURITY_CONFIG = Path.of(
			"src/main/java/com/gabolle/backend/auth/config/SecurityConfig.java");

	/**
	 * 허용 목록에 있지만 대응하는 엔드포인트가 지금은 없어도 되는 것. 비워 두는 것이 정답이다 —
	 * 줄이 늘어나는 것은 쓰지 않는 문을 열어 두고 있다는 뜻이므로 왜 지금 못 지우는지 적는다.
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
	 * 허용 목록의 어느 항목이 이 경로를 덮는가. 와일드카드를 한 마디 대치로 다룬다 —
	 * {@code "/api/v1/auth/oauth/*"} 는 {@code /api/v1/auth/oauth/{}} 를 덮지만
	 * {@code /api/v1/auth/oauth/{}/challenge} 는 덮지 않는다.
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
				// 허용 목록의 * 는 어떤 한 마디든 덮는다 — 경로 변수 자리만이 아니다.
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
	@DisplayName("🔴 인증이 필요한 경로가 허용 목록에 걸리지 않았다 — 와일드카드를 넓히면 여기서 걸린다")
	void authenticatedRoutesAreNotAccidentallyOpen() throws IOException {
		String source = Files.readString(SECURITY_CONFIG, StandardCharsets.UTF_8);

		// 막아야 할 경로 목록을 손으로 적지 않는다. 정책 표에서 유도해야 새 엔드포인트가 자동으로
		// 대상이 된다.
		//
		// 기준이 PRE_AUTH 가 아니라 PRE_AUTH + PUBLIC_TOKEN 이다. 공유 주소는 43글자 난수 표
		// 자체가 자격증명이라 로그인 없이 열리는 것이 기능이다 — 이 검사가 보는 것은 "로그인이
		// 필요한가" 가 아니라 "인증이 필요한가" 다.
		Set<String> mayBeOpen = RouteAuthorizationRegistryTest.openWithoutLoginRoutesForAudit();
		List<String> leaked = new ArrayList<>();
		for (String route : RouteAuthorizationRegistryTest.discoverRoutesForAudit()) {
			if (mayBeOpen.contains(route)) {
				continue;
			}
			String path = route.substring(route.indexOf(' ') + 1);
			if (allowlistCovers(source, path)) {
				leaked.add(route);
			}
		}

		assertThat(leaked)
				.withFailMessage("""
						로그인이 필요한 경로가 SecurityConfig 의 허용 목록에 걸립니다. 즉 인증
						없이 열려 있습니다.

						%s

						🔴 대개 와일드카드를 넓히다가 생깁니다. 허용 목록의 * 는 <b>어떤 한
						마디든</b> 덮으므로, "/api/v1/auth/oauth/*" 는 /oauth/{provider} 뿐 아니라
						/oauth/signup·/oauth/link 까지 엽니다. 마디 수가 같으면 이름과 무관하게
						덮인다는 것이 요점입니다.

						열어야 하는 경로라면 정책 표의 분류를 PRE_AUTH(로그인 전에 부른다)나
						PUBLIC_TOKEN(표 자체가 자격증명이다)으로 고치고 근거를 적어 주세요 —
						그러면 이 검사가 아니라 "로그인 없이 열리는 경로 개수 고정" 쪽이 그
						변경을 사람에게 보여 줍니다. 숫자를 고치는 diff 가 리뷰에 남는 것이
						요점입니다.""".formatted(String.join(System.lineSeparator(), leaked)))
				.isEmpty();
	}

	@Test
	@DisplayName("허용 목록을 실제로 읽었다 — 정규식이 아무것도 못 잡으면 이 검사가 무의미하다")
	void allowlistWasActuallyParsed() throws IOException {
		// 파싱이 실패해 빈 집합이 되면 위 확인이 "없는 경로가 없다" 며 초록이 된다. 개수를 정확히
		// 박지 않는 이유는 목록이 정당하게 바뀔 때마다 빨개지면 사람이 숫자만 고치기 때문이다 —
		// 대신 반드시 있어야 하는 항목 하나를 이름으로 확인한다.
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
