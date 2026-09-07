package com.gabolle.backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * S15P21E201-672 — {@code SecurityConfig} 의 허용 목록이 <b>실제로 있는 경로만</b> 열고 있는지 본다.
 *
 * <h2>🔴 무엇을 막는가 — 미래에 조용히 열리는 문</h2>
 * 허용 목록에 있는데 그런 엔드포인트가 <b>아직 없으면</b> 지금은 아무 일도 안 일어난다. 뚫을
 * 것이 없으니 스캐너도 아무 말을 안 한다. 문제는 <b>나중</b>이다. 몇 달 뒤 누가 그 경로로
 * 컨트롤러를 만들면, 그 사람은 인증을 켜거나 끄는 결정을 한 적이 없는데 <b>첫 커밋부터 인증
 * 없이 열려 있다.</b> 리뷰에도 안 보인다 — 그 MR 은 {@code SecurityConfig} 를 건드리지 않았기
 * 때문이다.
 *
 * <p>2026-09-07 인가 점검에서 실제로 셋을 찾았다. {@code /api/v1/auth/web/refresh},
 * {@code /api/v1/auth/web/logout}, {@code /api/v1/auth/oauth/*&#47;challenge} 가 허용 목록에
 * 있는데 그 경로를 매핑하는 컨트롤러가 없다. 웹 쿠키 인증과 challenge 발급은 설계 단계에서
 * 목록에 먼저 적히고 구현이 안 왔거나 빠진 것으로 보인다.
 *
 * <p>그래서 허용 목록에서 <b>고정 경로</b>(경로 변수가 없는 것)를 뽑아 실제 라우트와 대조한다.
 * 없는 것을 열어 두었으면 여기서 실패하고, 열어 둘 이유가 있으면 아래 {@code KNOWN_ABSENT} 에
 * 근거와 함께 적어야 통과한다.
 *
 * <h2>왜 경로 변수가 있는 항목은 안 보는가</h2>
 * {@code "/api/v1/auth/oauth/*"} 같은 와일드카드는 여러 실제 경로에 걸리고, 그 대응을 문자열로
 * 정확히 맞추려면 Spring 의 경로 매칭을 여기서 다시 구현해야 한다. 그건 이 검사보다 틀릴 확률이
 * 높다. 와일드카드가 무엇을 여는지는 {@code SecurityConfig} 주석과
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
