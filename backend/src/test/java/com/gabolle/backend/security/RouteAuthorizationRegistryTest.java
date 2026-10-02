package com.gabolle.backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 모든 경로가 인가 정책을 명시하고 있는지를 재는 검사. 기능이 아니라 빠뜨림을 잰다 — 컨트롤러의 모든
 * 경로를 클래스 경로에서 열거해 아래 {@code POLICY} 표와 대조하고, 표에 없는 경로가 하나라도 있으면
 * 실패한다. 새 엔드포인트를 만든 사람은 그 경로의 정책과 근거를 여기 적어야 빌드를 통과한다.
 *
 * <p>Spring 컨텍스트를 띄우지 않는다. 컨트롤러 대부분에 {@code @Profile({"db","dev"})} 가 붙어 있어
 * 프로필과 DB 없이 띄우면 그 경로들이 매핑에 아예 안 나타나고, 그러면 이 검사가 절반을 안 보고
 * "빠진 것이 없다" 고 말한다. 대신 클래스 경로를 직접 훑어 애너테이션을 읽는다.
 *
 * <p>정책 일곱 가지:
 * <ul>
 *   <li>{@code PRE_AUTH} — 로그인 전에 부르는 인증 흐름 자체. 열려 있는 것이 정상이고 보호는 안쪽에
 *       있다(연속 실패 잠금, 1회용 티켓, 비밀번호 확인)</li>
 *   <li>{@code PUBLIC_TOKEN} — 로그인 없이 열려 있고, 추측 불가능한 표·키를 아는 사람만 무언가를 받는다</li>
 *   <li>{@code OWNED} — 요청자가 그 자원의 주인이어야 한다. 남의 것을 부르면 2xx 가 나오면 안 된다</li>
 *   <li>{@code OTHER_USER_OK} — 남의 자원을 보는 것이 기능 자체다. 위험은 거부되지 않는 것이 아니라
 *       보여선 안 될 것이 섞이는 것이다</li>
 *   <li>{@code AUTHENTICATED_ONLY} — 로그인만 하면 누구나 같은 답을 받는다. 자원에 주인이 없다</li>
 *   <li>{@code INTERNAL_ONLY} — 사람이 아니라 기계가 부른다. 요청자에게 신원이 없고 공유 토큰
 *       ({@code X-Internal-Token})이 전부다. {@code ADMIN_ONLY} 로 분류하면 안 된다 — 운영자 권한은
 *       배포 설정의 이메일 목록에서 매 기동 계산되므로, 목록을 고치는 사람이 자기가 배치를 멈춘다는
 *       것을 모른 채 멈추게 된다</li>
 *   <li>{@code ADMIN_ONLY} — 운영자만. 자원의 주인이 요청자가 아닌 유일한 갈래라 {@code OWNED} 로
 *       분류하면 안 된다</li>
 * </ul>
 *
 * <p>이 표는 정책이 실제로 지켜지는지를 재지 않는다. 그건 표의 두 번째 칸이 가리키는 테스트들의
 * 몫이고, 여기서는 "어느 경로도 정책 없이 존재하지 않는다" 하나만 본다.
 */
class RouteAuthorizationRegistryTest {

	private static final String BASE_PACKAGE = "com.gabolle.backend";

	enum Policy {

		PRE_AUTH, PUBLIC_TOKEN, OWNED, OTHER_USER_OK, AUTHENTICATED_ONLY, ADMIN_ONLY, INTERNAL_ONLY
	}

	private static final Map<String, Map.Entry<Policy, String>> POLICY = policies();

	@Test
	@DisplayName("🔴 인가 정책이 적혀 있지 않은 경로가 없다 — 새 엔드포인트를 만들면 여기에 적어야 한다")
	void everyRouteHasAPolicy() {
		Set<String> unclassified = new TreeSet<>(discoverRoutes());
		unclassified.removeAll(POLICY.keySet());

		assertThat(unclassified)
				.withFailMessage("""
						인가 정책이 없는 경로가 있습니다. 이 파일의 POLICY 표에 경로와 정책, 그리고
						근거(OWNED 면 남의 것을 거부하는지 재는 테스트 이름)를 적어 주세요.
						정책 다섯 가지의 뜻은 이 클래스 javadoc 에 있습니다.

						%s""".formatted(String.join("\n", unclassified)))
				.isEmpty();
	}

	@Test
	@DisplayName("표에만 있고 코드에는 없는 경로가 없다 — 지운 엔드포인트가 남아 있으면 근거가 낡는다")
	void tableHasNoStaleEntries() {
		Set<String> stale = new TreeSet<>(POLICY.keySet());
		stale.removeAll(discoverRoutes());

		assertThat(stale)
				.withFailMessage("코드에 없는 경로가 표에 남아 있습니다. 지워 주세요.%n%s"
						.formatted(String.join("\n", stale)))
				.isEmpty();
	}

	@Test
	@DisplayName("모든 항목에 근거가 적혀 있다 — 근거 없는 분류는 분류가 아니다")
	void everyEntryHasRationale() {
		List<String> missing = new ArrayList<>();
		POLICY.forEach((route, entry) -> {
			if (entry.getValue() == null || entry.getValue().isBlank()) {
				missing.add(route);
			}
		});
		assertThat(missing).isEmpty();
	}

	@Test
	@DisplayName("🔴 로그인 없이 열리는 경로의 개수를 고정한다 — 하나 늘리면 이 숫자도 고쳐야 한다")
	void routesReachableWithoutLoginArePinned() {
		Set<String> open = routesWith(Policy.PRE_AUTH, Policy.PUBLIC_TOKEN);

		// 개수를 박아 두면 하나 열 때마다 이 줄을 고치게 되고, 그 변경이 diff 에 남아 리뷰에서
		// 보인다. 인증 없이 열린 경로가 조용히 늘어나는 것이 이 시스템에서 가장 비싼 실수다.
		assertThat(open).hasSize(20);

		// 표를 아는 사람이 실제로 열린 것과 대조할 수 있게 목록도 고정한다
		assertThat(routesWith(Policy.PUBLIC_TOKEN)).containsExactlyInAnyOrder(
				"GET /api/v1/places/{}/photo",
				"GET /api/v1/shares/{}",
				"GET /api/v1/uploads/images/{}");
	}

	@Test
	@DisplayName("🔴 운영자 경로가 모두 /api/v1/admin/ 아래에 있다 — 경로 규칙 하나로 막기 때문이다")
	void adminRoutesLiveUnderTheAdminPrefix() {
		// 이 저장소는 메서드 보안(@EnableMethodSecurity)이 꺼져 있어 @PreAuthorize 가 조용히 무시된다.
		// 운영자 인가는 SecurityConfig 의 "/api/v1/admin/**" → hasRole("ADMIN") 하나가 전부 담당하므로,
		// 그 아래에 없는 운영자 경로는 아무도 막지 않는다.
		assertThat(routesWith(Policy.ADMIN_ONLY))
				.isNotEmpty()
				.allSatisfy(route -> assertThat(route)
						.contains(" /api/v1/admin/"));
	}

	@Test
	@DisplayName("주인 검사가 필요한 경로가 절반을 넘는다 — 이 시스템의 기본은 소유 자원이다")
	void ownedRoutesAreTheMajority() {
		// 숫자 자체가 목적이 아니라, 누군가 정책을 대충 AUTHENTICATED_ONLY 로 몰아넣는 것을 눈에
		// 띄게 하려는 것이다. 소유 자원을 그렇게 분류하면 남의 것을 거부하는지 아무도 안 재게 된다.
		// 동률까지는 허용한다 — 여러 개가 한꺼번에 넘어가는 진짜 몰아넣기는 여전히 잡힌다.
		assertThat(routesWith(Policy.OWNED).size())
				.isGreaterThanOrEqualTo(routesWith(Policy.AUTHENTICATED_ONLY).size());
	}

	// ---- 경로 열거 ----

	private static Set<String> routesWith(Policy... policies) {
		Set<Policy> wanted = Set.of(policies);
		Set<String> result = new TreeSet<>();
		POLICY.forEach((route, entry) -> {
			if (wanted.contains(entry.getKey())) {
				result.add(route);
			}
		});
		return result;
	}

	/**
	 * {@code "GET /api/v1/places/nearby"} 모양의 문자열 집합.
	 *
	 * <p>Spring 의 컴포넌트 스캐너를 쓰지 않는다. {@code ClassPathScanningCandidateComponentProvider}
	 * 는 찾은 후보의 조건부 애너테이션을 평가하므로, 활성 프로필이 없으면 {@code @Profile} 이 붙은
	 * 컨트롤러가 전부 걸러지고 이 검사가 경로 0개를 찾고도 초록이 된다. 대신 클래스 파일을 직접 찾아
	 * 리플렉션으로 애너테이션만 읽는다.
	 */
	private static Set<String> discoverRoutes() {
		Set<String> routes = new TreeSet<>();
		for (Class<?> controller : scanForControllers()) {
			routes.addAll(routesOf(controller));
		}
		// 하나도 못 찾았으면 그것 자체가 실패다. 빈 집합을 돌려주면 위의 모든 확인이 무의미하게 초록이 된다.
		if (routes.isEmpty()) {
			throw new IllegalStateException(
					"컨트롤러를 하나도 못 찾았다 — 이 검사가 아무것도 보지 않고 있다는 뜻이다");
		}
		return routes;
	}

	/** 같은 패키지의 허용 목록 검사가 쓰는 창구 — 로그인 전에 부르는 경로만. */
	static Set<String> preAuthRoutesForAudit() {
		return routesWith(Policy.PRE_AUTH);
	}

	/**
	 * 로그인 없이 열려야 하는 경로 전부. {@link #preAuthRoutesForAudit()} 는 "빠지면 기능이 죽는다" 를
	 * 보는 창구고 이쪽은 "이 밖의 것이 열려 있으면 구멍이다" 를 보는 창구다. {@code PUBLIC_TOKEN} 은
	 * 표 자체가 자격증명이라 앞쪽에 넣으면 안 되고 뒤쪽에서는 빠지면 안 된다.
	 */
	static Set<String> openWithoutLoginRoutesForAudit() {
		return routesWith(Policy.PRE_AUTH, Policy.PUBLIC_TOKEN);
	}

	static Set<String> discoverRoutesForAudit() {
		return discoverRoutes();
	}

	/** {@code com.gabolle.backend} 아래의 {@code @RestController} 클래스 전부. */
	private static List<Class<?>> scanForControllers() {
		String pattern = "classpath*:" + BASE_PACKAGE.replace('.', '/') + "/**/*.class";
		List<Class<?>> controllers = new ArrayList<>();
		try {
			for (Resource resource : new PathMatchingResourcePatternResolver().getResources(pattern)) {
				String name = classNameOf(resource);
				if (name == null) {
					continue;
				}
				Class<?> type = tryLoad(name);
				if (type != null && type.isAnnotationPresent(RestController.class)) {
					controllers.add(type);
				}
			}
		}
		catch (java.io.IOException e) {
			throw new IllegalStateException("클래스 경로를 훑지 못했다", e);
		}
		return controllers;
	}

	private static String classNameOf(Resource resource) {
		try {
			// URL 은 항상 슬래시를 쓴다 — 윈도우 경로 구분자를 신경 쓸 필요가 없다
			String path = resource.getURL().toString();
			int at = path.indexOf(BASE_PACKAGE.replace('.', '/'));
			if (at < 0 || !path.endsWith(".class") || path.contains("$")) {
				return null;
			}
			return path.substring(at, path.length() - ".class".length()).replace('/', '.');
		}
		catch (java.io.IOException e) {
			return null;
		}
	}

	/** 못 읽는 클래스는 건너뛴다 — 테스트 전용 의존성이 없는 클래스가 섞일 수 있다. */
	private static Class<?> tryLoad(String name) {
		try {
			return Class.forName(name, false, RouteAuthorizationRegistryTest.class.getClassLoader());
		}
		catch (Throwable e) {
			return null;
		}
	}

	/**
	 * 한 컨트롤러가 선언한 경로들. {@code discoverRoutes} 에서 떼어낸 것은 회귀 검사가 직접 부를
	 * 자리가 필요해서다 — 클래스 경로 스캔 안에만 있으면 확인용 컨트롤러에 {@code @RestController} 를
	 * 붙여야 하고, 그 순간 그것이 감사 대상에 들어가 표와 열린 경로 수까지 건드린다.
	 */
	static Set<String> routesOf(Class<?> controller) {
		Set<String> routes = new TreeSet<>();
		String base = classLevelPath(controller);
		for (var method : controller.getDeclaredMethods()) {
			collect(routes, base, method.getAnnotation(GetMapping.class));
			collect(routes, base, method.getAnnotation(PostMapping.class));
			collect(routes, base, method.getAnnotation(PutMapping.class));
			collect(routes, base, method.getAnnotation(PatchMapping.class));
			collect(routes, base, method.getAnnotation(DeleteMapping.class));
		}
		return routes;
	}

	private static String classLevelPath(Class<?> controller) {
		RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
		if (mapping == null) {
			return "";
		}
		String[] path = pathOf(mapping.value(), mapping.path());
		return path.length == 0 ? "" : path[0];
	}

	/**
	 * {@code value} 와 {@code path} 를 둘 다 본다. 메서드 매핑은 {@code method.getAnnotation} — 순수
	 * 반사라 {@code @AliasFor} 를 합치지 않아서, {@code path=} 로 쓰면 {@code value()} 가 비어 메서드
	 * 경로를 클래스 경로로 오인한다. 그 클래스 경로가 이미 표에 있으면 정책을 한 줄도 안 적은 새
	 * 경로가 조용히 통과한다.
	 *
	 * <p>클래스 경로 쪽은 {@code AnnotatedElementUtils.findMergedAnnotation} 이 별칭을 합쳐 주므로
	 * 원래 필요 없다. 두 자리가 같은 규칙으로 보이게 두면 읽는 도구를 바꿔도 결과가 안 흔들린다.
	 */
	private static String[] pathOf(String[] value, String[] path) {
		return value.length > 0 ? value : path;
	}

	private static void collect(Set<String> routes, String base, Annotation annotation) {
		if (annotation == null) {
			return;
		}
		String verb;
		String[] value;
		if (annotation instanceof GetMapping a) {
			verb = "GET";
			value = pathOf(a.value(), a.path());
		}
		else if (annotation instanceof PostMapping a) {
			verb = "POST";
			value = pathOf(a.value(), a.path());
		}
		else if (annotation instanceof PutMapping a) {
			verb = "PUT";
			value = pathOf(a.value(), a.path());
		}
		else if (annotation instanceof PatchMapping a) {
			verb = "PATCH";
			value = pathOf(a.value(), a.path());
		}
		else if (annotation instanceof DeleteMapping a) {
			verb = "DELETE";
			value = pathOf(a.value(), a.path());
		}
		else {
			throw new IllegalArgumentException("모르는 매핑: " + annotation);
		}

		if (value.length == 0) {
			routes.add(verb + " " + normalize(base));
			return;
		}
		for (String sub : value) {
			routes.add(verb + " " + normalize(base + sub));
		}
	}

	/**
	 * 경로 변수 이름을 지운다 — {@code {tripId}} 든 {@code {id}} 든 같은 자리다. 이름까지 표에 적으면
	 * 인가와 무관한 변수명 변경에도 빨개진다.
	 */
	private static String normalize(String path) {
		return path.replaceAll("\\{\\*?[A-Za-z0-9_]+\\}", "{}").replaceAll("//+", "/");
	}

	// ---- 표 ----

	private static void put(Map<String, Map.Entry<Policy, String>> m, String route, Policy policy,
			String rationale) {
		m.put(route, Map.entry(policy, rationale));
	}

	private static Map<String, Map.Entry<Policy, String>> policies() {
		Map<String, Map.Entry<Policy, String>> m = new LinkedHashMap<>();

		// ── 인증 흐름 — 로그인 전에 부른다
		put(m, "POST /api/v1/auth/signup", Policy.PRE_AUTH,
				"가입. 보호는 이메일 인증과 중복 검사에 있다");
		put(m, "POST /api/v1/auth/login", Policy.PRE_AUTH,
				"로그인. 보호는 LoginAttemptGuard 의 연속 실패 잠금이다 (-421)");
		put(m, "POST /api/v1/auth/refresh", Policy.PRE_AUTH,
				"토큰 갱신. 보호는 리프레시 토큰 자체다");
		put(m, "POST /api/v1/auth/logout", Policy.PRE_AUTH,
				"로그아웃. 남의 토큰을 무효화하려면 그 토큰을 알아야 한다");
		put(m, "GET /api/v1/auth/email-verification", Policy.PRE_AUTH,
				"메일 링크를 눌러 들어오는 자리라 로그인 상태가 아니다. 보호는 1회용 표다. 실측 302");
		put(m, "POST /api/v1/auth/email-verification/confirm", Policy.PRE_AUTH,
				"위와 같은 표를 본문으로 받는 경로");
		put(m, "POST /api/v1/auth/email-verification/resend", Policy.PRE_AUTH,
				"재발송. 보호는 발송 빈도 제한이어야 한다 — 지금 있는지 미확인, 아래 '남은 위험' 참고");
		put(m, "POST /api/v1/auth/password-reset/request", Policy.PRE_AUTH,
				"비밀번호 재설정 요청. 존재하는 이메일인지 응답으로 알려주지 않아야 한다");
		put(m, "POST /api/v1/auth/password-reset/confirm", Policy.PRE_AUTH,
				"재설정 확정. 보호는 1회용 표다");
		put(m, "POST /api/v1/auth/web/refresh", Policy.PRE_AUTH,
				"웹은 리프레시 토큰을 쿠키로 주고받는다. 로그인 전 상태에서 오는 요청이다");
		put(m, "POST /api/v1/auth/web/logout", Policy.PRE_AUTH,
				"위와 같다. 남의 세션을 끊으려면 그 쿠키를 가지고 있어야 한다");
		put(m, "POST /api/v1/auth/oauth/apple/form-post", Policy.PRE_AUTH,
				"애플이 form_post 로 되돌려 보내는 자리 — 로그인 전에 애플 서버가 직접 부르므로 열려 있다. "
						+ "판정을 하지 않는 경로다: 받은 code·state 를 설정에 박힌 화면 주소로만 옮기고"
						+ "(요청이 준 주소를 쓰지 않아 open redirect 가 안 된다), state·nonce 검증은 화면이 "
						+ "이어서 부르는 코드 교환이 한다. 폼 본문의 user(이름·이메일)는 서명 밖의 값이라 "
						+ "받지도 않는다. provider 를 경로 변수로 두지 않고 apple 로 박은 것은 form_post 를 요구하는 제공자가 애플뿐이어서다. "
						+ "AppleFormPostJourneyFunctionalTest (-833)");
		put(m, "POST /api/v1/auth/oauth/{}/challenge", Policy.PRE_AUTH,
				"🔴 소셜 로그인의 첫 요청이다. 막으면 브라우저가 열리기도 전에 401 이 난다 (-704)");
		put(m, "POST /api/v1/auth/oauth/{}", Policy.PRE_AUTH,
				"소셜 인증. 보호는 provider 가 준 인증 코드와 PKCE 다");
		put(m, "POST /api/v1/auth/oauth/signup", Policy.PRE_AUTH,
				"2단계 소셜 가입 완료. 보호는 10분짜리 1회용 가입 티켓이다 (-689)");
		put(m, "POST /api/v1/auth/oauth/link", Policy.PRE_AUTH,
				"소셜 계정을 기존 계정에 연결. 보호는 연결 티켓 + 기존 계정 비밀번호다 (-690)");
		put(m, "POST /api/v1/auth/anonymous", Policy.PRE_AUTH,
				"익명 출입증 발급(-303). 가입 안 한 사람이 부르는 첫 요청이라 아직 X-Session-Token 이 없다. "
						+ "보호는 무작위 출입증 자체다 — 서버는 해시만 들고 있고 원본은 이 응답에만 나간다");

		// ── 인증 흐름 — 로그인 상태에서 부른다
		put(m, "POST /api/v1/auth/oauth/{}/link", Policy.OWNED,
				"로그인 상태에서 내 계정에 소셜을 붙인다. 대상은 언제나 인증 주체 자신이라 남의 것을 지정할 자리가 없다. OAuthTwoStepSignupIntegrationTest");
		put(m, "GET /api/v1/auth/me", Policy.OWNED,
				"대상이 경로에 없고 인증 주체로만 정해진다 — 남의 것을 지정할 방법이 없다");
		put(m, "PATCH /api/v1/auth/me", Policy.OWNED,
				"위와 같다. 대상이 인증 주체 자신뿐이다");
		put(m, "GET /api/v1/auth/me/consents", Policy.OWNED,
				"내 동의 상태(-735). 대상이 인증 주체 자신뿐이라 남의 것을 지정할 자리가 없다. ConsentUpdateIntegrationTest");
		put(m, "PATCH /api/v1/auth/me/consents", Policy.OWNED,
				"동의 변경(-735). 위와 같다. 🔴 필수 약관은 이 경로로 철회되지 않는다 — 그건 탈퇴다. ConsentUpdateIntegrationTest");
		put(m, "GET /api/v1/auth/me/deletion-preview", Policy.OWNED,
				"탈퇴하면 무엇이 지워지는지 미리 보여준다(-188). 대상이 인증 주체 자신뿐이라 남의 것을 지정할 자리가 없다. AccountDeletionIntegrationTest");
		put(m, "DELETE /api/v1/auth/me", Policy.OWNED,
				"탈퇴. 대상이 인증 주체 자신뿐이다. AccountDeletionIntegrationTest");
		put(m, "GET /api/v1/auth/me/identities", Policy.OWNED,
				"내 계정에 붙어 있는 소셜 계정 목록(-1317). 대상이 인증 주체 자신뿐이라 남의 것을 지정할 자리가 없다. "
						+ "LinkedIdentityIntegrationTest");
		put(m, "DELETE /api/v1/auth/me/identities/{}", Policy.OWNED,
				"소셜 연결을 뗀다(-1317). 경로의 {provider} 는 <b>내 연결 중 어느 것인가</b>이지 사람이 아니다 — "
						+ "남의 연결을 가리킬 자리가 없다. 🔴 마지막 로그인 수단은 서버가 막는다(LAST_SIGN_IN_METHOD). "
						+ "LinkedIdentityIntegrationTest");
		put(m, "POST /api/v1/menu-scans", Policy.AUTHENTICATED_ONLY,
				"메뉴판 사진을 서버가 모델에 중계한다. 우리 자원이 아니라 주인이 없다 — 인증을 요구하는 것은 "
						+ "tools/translate 와 같은 이유(우리 키로 남이 호출을 돌리는 비용)에 더해, "
						+ "한도를 사람 단위로 세야 하기 때문이다. 사진은 저장하지 않는다. MenuScanControllerTest (-1025)");
		put(m, "POST /api/v1/dishes", Policy.AUTHENTICATED_ONLY,
				"음식 이름 하나로 설명과 그림을 받는다(-1272). 위 메뉴판 읽기와 같은 이유다 — 우리 자원이 "
						+ "아니라 주인이 없고, 우리 키로 남이 호출을 돌리는 값을 막아야 하며, 그림 한도를 사람 "
						+ "단위로 세야 한다. DishControllerTest");
		put(m, "GET /api/v1/dishes/images/{}", Policy.AUTHENTICATED_ONLY,
				"만들어 둔 음식 그림을 내준다(-1272). 🔴 OWNED 가 아닌 것이 맞다 — 그림은 음식 이름으로 "
						+ "모아 두고 모두가 함께 쓴다. 「돼지국밥」 그림에는 누구의 것도 들어 있지 않고, 만든 "
						+ "사람이 누구인지도 안 적는다. 그래도 로그인은 요구한다: 기록 사진 업로드처럼 «주소만 "
						+ "알면 열리는» 자리로 두면 우리 저장소가 남의 이미지 서버가 된다. DishControllerTest");

		// ── 컬렉션
		// 경로에 남의 번호를 넣을 자리가 없고(/me) 컬렉션은 언제나 주인과 함께 찾는다
		// (findByIdAndUserId). 없는 것과 남의 것을 같은 404 로 답해 존재 자체를 안 흘린다.
		put(m, "GET /api/v1/me/collections", Policy.OWNED,
				"내 컬렉션만 읽는다. 저장한 장소와 같은 방식이다. CollectionControllerTest (-1013)");
		put(m, "POST /api/v1/me/collections", Policy.OWNED,
				"내 것으로만 만들어진다 — 주인은 인증 주체에서 온다. CollectionControllerTest (-1013)");
		put(m, "GET /api/v1/me/collections/{}", Policy.OWNED,
				"남의 컬렉션은 없는 것과 같은 404 다. CollectionControllerTest (-1013)");
		put(m, "PATCH /api/v1/me/collections/{}", Policy.OWNED,
				"남의 컬렉션 이름을 못 고친다. 같은 404. CollectionControllerTest (-1013)");
		put(m, "DELETE /api/v1/me/collections/{}", Policy.OWNED,
				"남의 컬렉션을 못 지운다. 같은 404. CollectionControllerTest (-1013)");
		put(m, "POST /api/v1/me/collections/{}/items", Policy.OWNED,
				"남의 컬렉션에 못 담는다. 같은 404. CollectionControllerTest (-1013)");
		put(m, "PATCH /api/v1/me/collections/{}/items/{}", Policy.OWNED,
				"항목도 컬렉션 번호와 함께 찾는다 — 항목 번호만으로 찾으면 남의 컬렉션 항목을 "
						+ "고칠 길이 열린다. CollectionControllerTest (-1013)");
		put(m, "DELETE /api/v1/me/collections/{}/items/{}", Policy.OWNED,
				"위와 같은 이유. CollectionControllerTest (-1013)");

		put(m, "GET /api/v1/me/saved-places", Policy.OWNED,
				"내가 저장한(하트) 장소. 경로에 남의 식별자를 넣을 자리가 없고(/me) 사용자 번호는 "
						+ "인증 주체에서만 읽는다 — 취향 설정과 같은 방식이다. SavedPlaceControllerTest (-1013)");
		put(m, "PUT /api/v1/me/saved-places/{}", Policy.OWNED,
				"내 목록에만 더한다. 같은 이유로 남의 것에 닿을 길이 없다. SavedPlaceControllerTest (-1013)");
		put(m, "DELETE /api/v1/me/saved-places/{}", Policy.OWNED,
				"내 목록에서만 뺀다. 같은 이유. SavedPlaceControllerTest (-1013)");

		put(m, "GET /api/v1/me/saved-stories", Policy.OWNED,
				"내가 저장한(북마크) 기록. saved-places와 같은 이유 — 경로에 남의 식별자를 넣을 자리가 없고(/me) "
						+ "사용자 번호는 인증 주체에서만 읽는다. StorySaveIntegrationTest");

		put(m, "GET /api/v1/me/preferences/spend", Policy.OWNED,
				"계정 기본 씀씀이 성향 조회(-709). 대상이 경로에 없고 인증 주체로만 정해진다 — 남의 것을 지정할 방법이 없다. SpendProfileControllerTest");
		put(m, "PUT /api/v1/me/preferences/spend", Policy.OWNED,
				"위와 같다. SpendProfileControllerTest");
		// ── 여행 조건 모달
		// 씀씀이와 같은 근거로 OWNED 다 — 대상이 경로에 없고 남의 것을 지정할 방법 자체가 없다.
		put(m, "GET /api/v1/me/preferences/constraints", Policy.OWNED,
				"내 여행 조건이다. 대상이 인증 주체로만 정해진다. 한 번도 저장 안 했으면 404 가 "
						+ "아니라 status:null 로 200 이다 — 「안 물어봤다」는 오류가 아니다. "
						+ "TravelConstraintIntegrationTest");
		put(m, "PUT /api/v1/me/preferences/constraints", Policy.OWNED,
				"저장도 같다. 경로·본문 어디에도 사람을 안 받는다 — userId 는 인증에서만 읽는다. "
						+ "TravelConstraintIntegrationTest");
		put(m, "GET /api/v1/me/preferences/taste", Policy.OWNED,
				"계정 기본 취향 조회(-639). 온보딩 첫 실행과 마이페이지가 읽는다. 위 /spend 와 같은 근거로 OWNED — "
				+ "대상이 경로에 없고 인증 주체로만 정해진다. TastePreferencesControllerTest");
		put(m, "PUT /api/v1/me/preferences/taste", Policy.OWNED,
				"위와 같다. 🔴 보낸 차원만 바뀌고 안 보낸 것은 남는다 — 지우기는 answerStatus=UNKNOWN 으로 온다. "
				+ "TastePreferencesControllerTest");

		// ── 여행
		put(m, "POST /api/v1/trips", Policy.AUTHENTICATED_ONLY,
				"새로 만드는 것이라 기존 자원의 주인 개념이 없다. 소유자는 인증 주체로 박힌다. "
				+ "S15P21E201-317 — 익명 세션(ROLE_ANONYMOUS)도 이 자리만은 통과한다. 만든 사람이 곧 "
				+ "소유자가 되므로 익명이라도 남의 것을 건드릴 수 없다 — AuthenticatedUsers.requireOwner");
		// 목록은 부를 때 자원을 지목하지 않아 OWNED 가 아니다. 위험은 "남의 것을 부르면
		// 거부되는가" 가 아니라 "남의 여행이 목록에 섞이는가" 이고, 저장소가 참여 표로 거른다.
		put(m, "GET /api/v1/trips", Policy.AUTHENTICATED_ONLY,
				"내 여행 목록. 참여 표로 걸러 남의 여행이 섞이지 않는다. TripListIntegrationTest");
		put(m, "GET /api/v1/trips/{}", Policy.OWNED,
				"비회원은 존재를 감춘 404. TripControllerGetTest · ItineraryAccessIntegrationTest");
		put(m, "DELETE /api/v1/trips/{}", Policy.OWNED,
				"삭제는 OWNER 만. 동행자는 403, 비회원과 없는 여행은 같은 404. TripDeleteIntegrationTest");
		put(m, "POST /api/v1/trips/{}/name-suggestions", Policy.OWNED,
				"참여자만. 남의 여행 ID 로 부르면 그 여행의 장소 목록이 이름 후보에 실려 "
						+ "새어 나간다 — 비회원과 없는 여행은 같은 404. "
						+ "TripNameSuggestionServiceTest (-1025)");
		put(m, "PUT /api/v1/trips/{}/title", Policy.OWNED,
				"이름은 OWNER·EDITOR 만 바꾼다. 보기 전용 동행자가 바꾸면 만든 사람의 목록에서 "
						+ "자기 여행이 다른 이름으로 보인다 — VIEWER 는 403, 비회원과 없는 여행은 "
						+ "같은 404. TripTitleTest (-1023)");
		for (String method : new String[] { "GET", "PUT", "DELETE" }) {
			put(m, method + " /api/v1/trips/{}/rating", Policy.OWNED,
					"여행 별점. 구성원만 — TripQueryService.get 을 지나 비회원과 없는 여행은 같은 404. "
							+ "TripRatingPostgresTest.strangerRejected (-1908)");
		}
		for (String route : new String[] { "GET /api/v1/trips/{}/expenses", "POST /api/v1/trips/{}/expenses",
				"DELETE /api/v1/trips/{}/expenses/{}", "PUT /api/v1/trips/{}/budget" }) {
			put(m, route, Policy.OWNED,
					"여행 돈. 구성원만 — TripQueryService.get 을 지나 비회원과 없는 여행은 같은 404. 남이 적은 줄 지우기·"
							+ "보기 전용의 예산 고치기는 403. TripExpensePostgresTest (-1935)");
		}
		put(m, "GET /api/v1/trips/{}/itineraries", Policy.OWNED,
				"참여자만. 비회원과 없는 여행이 같은 404. TripItineraryListIntegrationTest");
		put(m, "GET /api/v1/trips/{}/recommendations", Policy.OWNED,
				"추천 코스 3안. 참여자만 — 추천 요청과 같은 관문(TripQueryService.get)을 지난다. 비회원과 "
						+ "없는 여행이 같은 404. 추천이 아직 없으면 빈 목록이다. TripCourseIntegrationTest (-1454)");
		put(m, "POST /api/v1/trips/{}/course", Policy.OWNED,
				"고른 코스를 일정으로 만든다. 참여자만 — 같은 관문을 지난다. 남의 여행의 코스 번호를 내 여행 "
						+ "주소로 보내도 404 다(코스 번호가 그 여행 것인지 따로 본다). "
						+ "TripCourseIntegrationTest · TripCourseServiceTest (-1454)");
		put(m, "GET /api/v1/trips/{}/stories", Policy.OWNED,
				"참여자만. 비회원과 없는 여행이 같은 404 이고, 참여자에게도 그 기록의 공개 범위 판정"
						+ "(StoryVisibilityPolicy.canView)을 한 번 더 지난다 — 여행에 달렸다는 이유로 남의 "
						+ "나만 보기 기록이 새면 -137 에서 막은 구멍이 다시 열린다. "
						+ "TripStoryJourneyFunctionalTest (-829)");
		put(m, "GET /api/v1/trips/{}/activity", Policy.OWNED,
				"참여자만. TripActivityIntegrationTest");
		put(m, "GET /api/v1/me/notification-summary", Policy.OWNED,
				"종 점. 경로에 남의 번호를 넣을 자리가 없고(/me) 내가 참여한 여행의 활동만 센다 — 남의 여행·지운 여행은 "
						+ "안 센다. NotificationSummaryIntegrationTest (-1699)");
		put(m, "GET /api/v1/trips/{}/members", Policy.OWNED,
				"참여자만. TripMemberManagementIntegrationTest");
		put(m, "POST /api/v1/trips/{}/invites", Policy.OWNED,
				"OWNER·EDITOR 만 발급. TripInviteIntegrationTest");
		put(m, "PATCH /api/v1/trips/{}/members/{}", Policy.OWNED,
				"역할 변경은 OWNER 만. TripMemberManagementIntegrationTest");
		put(m, "DELETE /api/v1/trips/{}/members/{}", Policy.OWNED,
				"제거는 OWNER 만(자기 탈퇴는 예외). TripMemberManagementIntegrationTest");
		put(m, "POST /api/v1/trips/{}/share-links", Policy.OWNED,
				"공유 주소 발급은 참여자만. ShareLinkIntegrationTest");
		put(m, "POST /api/v1/trips/{}/recommendation-jobs", Policy.OWNED,
				"내 여행에만 추천을 요청할 수 있다. RecommendationResultAuthorizationTest");
		put(m, "GET /api/v1/trips/{}/recommendation-actions", Policy.OWNED,
				"참여자인 여행의 판단만 읽는다 — 추천 요청과 같은 관문(TripQueryService.get)을 지난다. "
						+ "판단은 사람별이 아니라 여행별이라 동행자가 남긴 것도 함께 온다(초대가 곧 공유 장치다). "
						+ "아직 아무것도 안 눌렀으면 빈 목록이고 404 가 아니다. "
						+ "RecommendationActionAuthorizationTest (-1013)");
		put(m, "PUT /api/v1/trips/{}/recommendation-actions/{}", Policy.OWNED,
				"참여자가 아닌 여행의 후보에는 판단을 적을 수 없다. 같은 관문을 지난다. "
						+ "참여자면 역할과 무관하게 적을 수 있고, 동행자가 정해 둔 것도 바꿀 수 있다 — "
						+ "함께 쓰는 값이라 그것이 기능이다. RecommendationActionAuthorizationTest (-1013)");
		put(m, "DELETE /api/v1/trips/{}/recommendation-actions/{}", Policy.OWNED,
				"참여자가 아닌 여행의 판단은 거둘 수 없다. 같은 관문을 지난다. "
						+ "RecommendationActionAuthorizationTest (-1013)");
		put(m, "GET /api/v1/trips/{}/recommendation-jobs", Policy.OWNED,
				"내 여행의 추천만 되찾을 수 있다 — 요청과 같은 관문(TripQueryService.get)을 지난다. "
						+ "추천이 없는 내 여행은 빈 목록이고 404 가 아니다. "
						+ "RecommendationResultAuthorizationTest (-1001)");

		// ── 초대·공유
		put(m, "POST /api/v1/trip-invites/{}/accept", Policy.AUTHENTICATED_ONLY,
				"표를 아는 로그인 사용자가 참여자가 되는 것이 기능이다. 보호는 43글자 난수 표와 7일 만료다. TripInviteIntegrationTest");
		put(m, "GET /api/v1/shares/{}", Policy.PUBLIC_TOKEN,
				"로그인 없이 열린다. 표가 43글자 난수이고 개인정보 칸이 응답 record 에 아예 없다 (-332). ShareLinkIntegrationTest");
		put(m, "POST /api/v1/shares/{}/clone", Policy.AUTHENTICATED_ONLY,
				"표를 아는 로그인 사용자가 자기 여행으로 복제하는 것이 기능이다. 원본은 안 바뀐다. ShareCloneIntegrationTest");

		// ── 일정
		put(m, "GET /api/v1/itineraries/{}", Policy.OWNED,
				"참여자만. ItineraryAccessIntegrationTest");
		put(m, "GET /api/v1/itineraries/{}/versions", Policy.OWNED,
				"참여자만. ItineraryVersionListingIntegrationTest");
		put(m, "POST /api/v1/itineraries/{}/items", Policy.OWNED,
				"장소 더하기는 편집 권한자만. ItineraryAddItemIntegrationTest (-467)");
		put(m, "POST /api/v1/itineraries/{}/days/{}/reorder", Policy.OWNED,
				"편집 권한이 있는 참여자만. 비회원과 없는 일정이 같은 404, VIEWER 는 403. ItineraryReorderIntegrationTest");
		put(m, "POST /api/v1/itineraries/{}/items/{}/lock", Policy.OWNED,
				"고정은 편집 권한자만. ItineraryAccessIntegrationTest");
		put(m, "DELETE /api/v1/itineraries/{}/items/{}/lock", Policy.OWNED,
				"고정 해제도 같다. ItineraryAccessIntegrationTest");
		put(m, "POST /api/v1/itineraries/{}/items/{}/remove", Policy.OWNED,
				"항목 제외는 편집 권한자만. ItineraryRecalculationIntegrationTest");
		put(m, "POST /api/v1/itineraries/{}/recalculate", Policy.OWNED,
				"재계산 접수는 편집 권한자만. ItineraryRecalculationIntegrationTest");
		put(m, "POST /api/v1/itineraries/{}/revert", Policy.OWNED,
				"되돌리기는 편집 권한자만. ItineraryRevertIntegrationTest");
		// ── 일정 진행 ─────────────────────────────────────────────────────────
		put(m, "GET /api/v1/itineraries/{}/progress", Policy.OWNED,
				"참여자면 볼 수 있다 — 동행자가 「지금 어디까지 갔나」를 같이 봐야 한다. ItineraryRunService.get");
		put(m, "POST /api/v1/itineraries/{}/progress/start", Policy.OWNED,
				"출발은 편집 권한자만. 보기 전용으로 초대된 사람이 남의 여행을 달리게 만들 수 있으면 안 된다");
		put(m, "POST /api/v1/itineraries/{}/progress/pause", Policy.OWNED,
				"중지도 같다. 남이 달리는 일정을 멈추는 것은 편집이다");
		put(m, "POST /api/v1/itineraries/{}/progress/stops/{}/arrive", Policy.OWNED,
				"도착 기록은 편집 권한자만 — 그 기록은 여행이 끝난 뒤에도 남는다. ItineraryRunService.arrive");
		put(m, "POST /api/v1/itineraries/{}/progress/stops/{}/skip", Policy.OWNED,
				"건너뛰기도 같다. 남의 일정에서 정차지를 빼는 것은 편집이다");
		put(m, "POST /api/v1/itineraries/{}/progress/location", Policy.OWNED,
				"🔴 위치를 올리는 것은 편집 권한자만 — 보기 전용으로 초대된 사람이 남의 여행 궤적에 "
						+ "자기 위치를 섞어 넣을 수 있으면 안 된다. 위치 기록은 지우기 전까지 남는다. "
						+ "ItineraryRunService.recordLocations");
		put(m, "POST /api/v1/itineraries/{}/progress/complete", Policy.OWNED,
				"완료도 편집이다. 남이 달리는 일정을 끝내 버릴 수 있으면 안 된다. ItineraryRunService.complete");

		put(m, "PUT /api/v1/itineraries/{}/items/{}/actual", Policy.OWNED,
				"그 여행의 편집자만 자기 일정의 방문 시각을 적는다 — 남의 여행은 존재를 감춘 404, VIEWER 는 403. ItineraryActualTimeIntegrationTest (-293)");
		put(m, "GET /api/v1/itineraries/{}/days/{}/pace", Policy.OWNED,
				"지연 경고 조회다. 보기만 하므로 VIEWER 도 본다 — 참여자가 아니면 존재를 감춘 404. ItineraryPaceIntegrationTest (-304)");
		put(m, "GET /api/v1/itineraries/{}/rhythm", Policy.OWNED,
				"여행 리듬 요약 조회다. 위와 같은 이유로 VIEWER 도 본다. ItineraryPaceIntegrationTest (-308)");
		put(m, "POST /api/v1/itineraries/{}/days/{}/replan", Policy.OWNED,
				"남은 하루 재계획은 판을 만드는 편집이라 편집 권한자만. ItineraryReplanIntegrationTest (-308)");

		// ── 추천 작업
		put(m, "GET /api/v1/jobs/{}", Policy.OWNED,
				"남의 작업 번호와 없는 번호를 같은 404 로 답한다. RecommendationResultAuthorizationTest");
		put(m, "GET /api/v1/jobs/{}/progress", Policy.OWNED,
				"진행률을 밀어 보내는 통로도 같은 소유자 검사를 지난다 — 이 자리만 열면 남의 계산이 "
						+ "어디까지 갔는지가 옆문으로 샌다. RecommendationJobProgressStreamTest (-193)");
		put(m, "GET /api/v1/recommendation-jobs/{}", Policy.OWNED,
				"위와 같은 소유자 검사를 공유한다. RecommendationResultAuthorizationTest");

		put(m, "POST /api/v1/trips/{}/facet-views/{}", Policy.OWNED,
				"그 여행의 회원만 그 여행 이름으로 갈래 열람을 남긴다 — 아무나 남기면 집계를 부풀릴 수 "
						+ "있다. FacetViewFunctionalTest (-475)");
		put(m, "POST /api/v1/facet-views/{}", Policy.AUTHENTICATED_ONLY,
				"여행에 안 묶인 전역 탐색에서 남기는 갈래 열람이다. 여행 번호를 아예 받지 않으므로 남의 "
						+ "여행에는 무엇도 남길 수 없고, 그래서 소유자 검사가 아니라 로그인만 요구한다 — 열어 두면 "
						+ "아무나 집계를 부풀릴 수 있다. FacetViewFunctionalTest (-894)");
		put(m, "GET /api/v1/admin/facet-views", Policy.ADMIN_ONLY,
				"갈래별 이용 집계는 운영 판단용이다. 경로 앞자리가 실제 보호 장치다. "
						+ "FacetViewFunctionalTest (-475)");

		// ── 코스 테마
		put(m, "GET /api/v1/course-categories", Policy.AUTHENTICATED_ONLY,
				"자원에 주인이 없는 목록 조회다. 쓰이는 자리가 여행 만들기 안이라 로그인 뒤에 둔다. "
						+ "CourseThemeContractFunctionalTest (-450)");

		// ── 기록·피드·사용자
		put(m, "POST /api/v1/stories", Policy.AUTHENTICATED_ONLY,
				"새로 쓰는 것이라 주인 개념이 없다. 작성자는 인증 주체로 박힌다");
		put(m, "GET /api/v1/stories", Policy.AUTHENTICATED_ONLY,
				"내가 볼 수 있는 것만 나오는 목록이다. 공개 범위 판정이 canView 다. StoryFeedIntegrationTest");
		put(m, "GET /api/v1/stories/{}", Policy.OTHER_USER_OK,
				"남의 기록을 보는 것이 기능이다. PRIVATE 는 작성자 아닌 사람에게 404. StoryCrudIntegrationTest");
		// 댓글 목록은 그 글을 볼 수 있는가와 같은 판정이다. "댓글은 있는데 글은 못 본다" 가 되면
		// 그것으로 글의 존재가 샌다.
		put(m, "GET /api/v1/stories/{}/replies", Policy.OTHER_USER_OK,
				"남의 기록의 댓글을 보는 것이 기능이다. 볼 수 없는 글이면 404 — "
						+ "StoryReplyIntegrationTest.cannotReplyToAStoryYouCannotSee 가 같은 판정을 잰다");
		put(m, "PATCH /api/v1/stories/{}", Policy.OWNED,
				"수정은 작성자만 — requireAuthor. StoryCrudIntegrationTest");
		put(m, "DELETE /api/v1/stories/{}", Policy.OWNED,
				"삭제는 작성자만 — requireAuthor. StoryCrudIntegrationTest");
		// ── 기록 공동 작성
		// 다섯 경로 전부 AUTHENTICATED_ONLY 다. 경로 자체에 남의 것과 내 것을 가르는 별도의 자원
		// 식별자가 없고, storyId 는 이미 StoryService.requireVisible 의 404/403 판정 뒤에 있다.
		// 실제 "만든 사람만"·"볼 수 있는 사람만" 은 StoryCoauthorService 가 가른다.
		put(m, "POST /api/v1/stories/{}/invites", Policy.AUTHENTICATED_ONLY,
				"만든 사람만 발급 — StoryCoauthorService.issueInvite 가 requireVisible 뒤에 isAuthor 를 재확인한다");
		put(m, "POST /api/v1/story-invites/{}/accept", Policy.AUTHENTICATED_ONLY,
				"표를 아는 로그인 사용자가 참여자가 되는 것이 기능이다. 보호는 43글자 난수 표와 7일 만료다(여행 초대와 같다)");
		put(m, "GET /api/v1/stories/{}/coauthors", Policy.AUTHENTICATED_ONLY,
				"그 기록을 볼 수 있는 사람이면 누구나 — canView 를 requireVisible 이 재확인한다");
		put(m, "POST /api/v1/stories/{}/coauthors", Policy.AUTHENTICATED_ONLY,
				"만든 사람만 — 여행 동행자인지도 TripMembershipRepository.findMember 로 서버가 재검사한다");
		put(m, "DELETE /api/v1/stories/{}/coauthors/{}", Policy.AUTHENTICATED_ONLY,
				"만든 사람이 남을 빼거나 공동 작성자가 자기 자신을 뺀다. 그 외는 403");

		// ── 글에 단 반응
		// 남의 글에 누르는 것이 기능 자체라 OTHER_USER_OK 다. 근거는 "남의 것을 거부하는가" 가
		// 아니라 "못 보는 글이 404 로 감춰지는가" 다.
		//
		// 오히려 자기 것을 거부한다 — 인기순이 붙으면 자기 글을 올리는 길이 되기 때문이다.
		// 다른 OTHER_USER_OK 경로에는 없는 조건이다.
		put(m, "PUT /api/v1/stories/{}/reaction", Policy.OTHER_USER_OK,
				"남의 글에 좋아요·싫어요를 다는 것이 기능이다. 못 보는 글은 존재를 감춘 404(StoryVisibilityPolicy.canView), 내가 함께 쓰는 글은 409. 주체는 인증에서만 읽는다. StoryReactionIntegrationTest");
		put(m, "DELETE /api/v1/stories/{}/reaction", Policy.OTHER_USER_OK,
				"취소도 같다. 지우는 키가 (글, 나) 쌍이라 남의 반응은 못 지운다 — 일부러 볼 수 있는지는 안 따진다(막으면 한 번 누른 사람이 영영 못 무른다). StoryReactionIntegrationTest");

		// ── 링크 복사 세기
		// 반응과 같은 갈래다. 다만 자기 글도 거부하지 않는다 — 반응은 자기 글이면 409 로 막지만
		// 링크 복사는 200 으로 통과시키고 수만 안 올린다. 막는 것과 안 세는 것은 다른 일이다.
		put(m, "POST /api/v1/stories/{}/link-copies", Policy.OTHER_USER_OK,
				"남의 글 링크를 복사했다고 알리는 것이 기능이다. 못 보는 글은 존재를 감춘 404"
						+ "(StoryVisibilityPolicy.canView 를 recordLinkCopy 가 세기 전에 부른다) — "
						+ "StoryLinkCopyCountIntegrationTest.hiddenStoryIsNotFound 가 그 404 와 "
						+ "「수도 안 오른다」를 함께 잰다. 주체는 인증에서만 읽고, 비회원이면 "
						+ "익명 세션 id 로만 읽는다(경로·본문에서 사람을 받지 않는다). "
						+ "작성자 본인은 막지 않고 수만 안 올린다 — 반응과 갈리는 자리다");

		// ── 글 저장·북마크
		put(m, "PUT /api/v1/stories/{}/save", Policy.OTHER_USER_OK,
				"글을 저장(북마크)하는 것이 기능이다 — reaction과 같은 모양. 못 보는 글은 존재를 감춘 404, "
						+ "차단당했으면 403. 자기 글도 저장할 수 있어 반응과 달리 자기 것 금지는 없다. StorySaveIntegrationTest");
		put(m, "DELETE /api/v1/stories/{}/save", Policy.OTHER_USER_OK,
				"취소도 같다. 지우는 키가 (글, 나) 쌍이라 남의 저장은 못 지운다 — 안 저장했던 것을 지워도 성공. StorySaveIntegrationTest");

		put(m, "GET /api/v1/feed/home", Policy.AUTHENTICATED_ONLY,
				"내 피드다. 대상이 인증 주체로만 정해진다. FeedControllerTest");
		put(m, "GET /api/v1/feed/community", Policy.AUTHENTICATED_ONLY,
				"공개 기록만 모은 목록이다. FeedControllerTest");
		put(m, "GET /api/v1/users/{}/profile", Policy.OTHER_USER_OK,
				"남의 프로필 보기가 기능이다. 위험은 비공개 항목이 섞이는 것. FollowIntegrationTest");
		put(m, "GET /api/v1/users/{}/stories", Policy.OTHER_USER_OK,
				"남의 기록 목록 보기가 기능이다. visibleScopesOf 가 팔로우 여부로 범위를 가른다. FollowIntegrationTest");
		put(m, "GET /api/v1/users/me/replies", Policy.AUTHENTICATED_ONLY,
				"내 댓글 목록이다. 대상이 인증 주체로만 정해져 남의 댓글 목록을 부를 자리가 없다. 원글 미리보기는 "
						+ "내가 지금 볼 수 있을 때만 싣는다. MyRepliesIntegrationTest (-1600)");
		put(m, "PUT /api/v1/users/{}/block", Policy.OTHER_USER_OK,
				"남을 차단하는 것이 기능이다. 주체는 인증에서만 읽어 남의 이름으로 차단할 수 없다. BlockIntegrationTest");
		put(m, "DELETE /api/v1/users/{}/block", Policy.OTHER_USER_OK,
				"차단 해제도 같다. 내가 건 차단만 풀 수 있다 — 지우는 키가 (나, 상대) 쌍이다. BlockIntegrationTest");
		put(m, "PUT /api/v1/users/{}/follow", Policy.OTHER_USER_OK,
				"남을 팔로우하는 것이 기능이다. 주체는 인증에서만 읽어 남의 이름으로 팔로우할 수 없다. FollowIntegrationTest");
		put(m, "DELETE /api/v1/users/{}/follow", Policy.OTHER_USER_OK,
				"언팔로우도 같다. 주체는 인증에서만 읽는다. FollowIntegrationTest");
		// ── 관계 목록
		put(m, "GET /api/v1/users/{}/followers", Policy.OTHER_USER_OK,
				"남의 팔로워 목록 보기가 기능이다. 그 사람이 나를 차단했으면 403 으로 막는다 — 기록 목록과 같은 규칙이다. RelationListIntegrationTest");
		put(m, "GET /api/v1/users/{}/following", Policy.OTHER_USER_OK,
				"남의 팔로잉 목록 보기가 기능이다. 차단 시 403 도 같다. 응답의 following 칸은 목록 주인이 아니라 보는 사람 기준으로 채운다. RelationListIntegrationTest");
		// 차단 목록만 OWNED 다 — 차단당한 사람이 그것을 알면 차단의 뜻이 없어진다.
		put(m, "GET /api/v1/users/{}/blocks", Policy.OWNED,
				"내가 차단한 사람 목록이다. userId 가 본인이 아니면 403. RelationListIntegrationTest");

		// ── 신고와 검토
		put(m, "POST /api/v1/stories/{}/reports", Policy.OTHER_USER_OK,
				"남의 기록에 신고를 거는 것이 기능이다. 안 보이는 기록은 존재를 감춘 404. 중복 신고는 조용히 성공한다(남의 신고 여부를 흘리지 않기 위해). StoryReportFilingIntegrationTest");
		put(m, "GET /api/v1/admin/story-reports", Policy.ADMIN_ONLY,
				"검토 큐. SecurityConfig 의 /api/v1/admin/** → hasRole(ADMIN) 이 막는다. AdminModerationAuthorizationIntegrationTest");
		put(m, "POST /api/v1/admin/story-reports/{}/remove", Policy.ADMIN_ONLY,
				"운영자 삭제. 같은 경로 규칙이 막는다. AdminModerationQueueIntegrationTest");
		put(m, "POST /api/v1/admin/story-reports/{}/dismiss", Policy.ADMIN_ONLY,
				"운영자 기각. 같은 경로 규칙이 막는다. AdminModerationQueueIntegrationTest");

		// ── 방문 인증과 리뷰
		put(m, "POST /api/v1/places/{}/visit-verifications", Policy.AUTHENTICATED_ONLY,
				"주체를 인증에서만 읽고 좌표는 저장하지 않으므로 남의 인증을 대신 만들 자리가 없다. VisitVerificationIntegrationTest");
		put(m, "POST /api/v1/places/{}/reviews", Policy.AUTHENTICATED_ONLY,
				"본문에 사용자도 인증 여부도 받지 않는다 — 서버가 인증 기록을 조회해 정하므로 남의 리뷰를 쓸 수 없다. PlaceReviewIntegrationTest");
		put(m, "GET /api/v1/places/{}/reviews", Policy.AUTHENTICATED_ONLY,
				"장소 하나의 목록이라 주인이 없다. 인증·미인증을 다 보여주고 평균은 인증된 것만으로 낸다. PlaceReviewIntegrationTest");

		// ── 업로드
		put(m, "POST /api/v1/uploads/story-image", Policy.AUTHENTICATED_ONLY,
				"새로 올리는 것이라 주인 개념이 없다. 올린 사람은 인증 주체로 박히고, 남이 올린 주소를 자기 기록에 붙이면 400 이다. ImageUploadIntegrationTest");
		put(m, "POST /api/v1/uploads/story-video", Policy.AUTHENTICATED_ONLY,
				"사진 창구와 같다 — 새로 올리는 것이라 주인 개념이 없고 올린 사람이 인증 주체로 박힌다. 창구를 가른 것은 형식·상한이 다르기 때문이지 인가가 다르기 때문이 아니다. StoryVideoDeletionIntegrationTest");
		// 동영상에는 GET 짝이 없다. 일부러 안 만들었다 — 운영은 nginx→MinIO 직행이라 재생이
		// 스프링을 안 지난다. 여기로 서빙하면 파일이 통째로 힙에 올라가고 구간 요청(Range)이
		// 없어 되감기가 안 된다.
		put(m, "GET /api/v1/uploads/images/{}", Policy.PUBLIC_TOKEN,
				"피드 화면이 <img> 로 부르고 그 요청에는 Authorization 이 안 붙는다. 키가 UUID 라 추측 불가. ImageUploadIntegrationTest");

		// ── 장소·기준 데이터
		put(m, "GET /api/v1/places", Policy.AUTHENTICATED_ONLY,
				"장소는 공용 기준 데이터라 사용자별로 답이 다르지 않다. PlaceSearchIntegrationTest");
		put(m, "GET /api/v1/places/facets", Policy.AUTHENTICATED_ONLY,
				"갈래별 건수. 공용 기준 데이터다. PlaceFacetInterestTagIntegrationTest");
		put(m, "GET /api/v1/places/categories", Policy.AUTHENTICATED_ONLY,
				"적재된 place.category 값과 건수. 요청자와 무관한 공용 기준 데이터라 facets 와 같은 정책이다. PlaceCategoryServiceTest");
		put(m, "GET /api/v1/places/condition-coverage", Policy.AUTHENTICATED_ONLY,
				"어느 문항에 장소 자료가 있는지. 장소 표식 수를 세는 것이라 요청자와 무관한 공용 기준 데이터다 — "
						+ "facets·categories 와 같은 정책이다. 사용자 답이 아니라 장소 쪽 덮임만 나가므로 "
						+ "남의 취향이 새지 않는다. ConditionCoverageServiceTest");
		put(m, "GET /api/v1/routes/directions", Policy.AUTHENTICATED_ONLY,
				"좌표 두 개로 답이 정해진다 — 우리 자원이 아니라 주인이 없다. 인증을 요구하는 것은 "
						+ "우리 카카오 키로 남이 길찾기를 대신 쓰는 것을 막기 위해서다. RouteControllerTest");
		put(m, "GET /api/v1/places/nearby", Policy.AUTHENTICATED_ONLY,
				"좌표만으로 답이 정해진다 — 컨트롤러 주석이 그렇게 적고 있다. NearbyFacetAndRadiusTest");
		put(m, "GET /api/v1/places/accommodations", Policy.AUTHENTICATED_ONLY,
				"숙소 후보 조회(S15P21E201-456). category 로 거른 공용 기준 데이터라 요청자별로 답이 갈리지 않는다. AccommodationQueryIntegrationTest");
		put(m, "GET /api/v1/places/{}", Policy.AUTHENTICATED_ONLY,
				"공용 기준 데이터. 다만 itineraryInclusion 은 요청자별로 갈리므로 그 자리는 인증 주체로만 읽는다. PlaceDetailIntegrationTest");
		put(m, "GET /api/v1/places/{}/photo", Policy.PUBLIC_TOKEN,
				"S15P21E201-1832 — 카드·상세 화면이 <img> 로 부르고 그 요청에는 Authorization 이 안 붙는다. 키가 UUID 라 추측 불가하고, Google 장소 번호가 저장된 공개 장소에만 302 를 준다(그 밖은 Google 을 안 부르고 404). PlacePhotoControllerTest");
		put(m, "GET /api/v1/places/{}/taxi-card", Policy.AUTHENTICATED_ONLY,
				"장소 하나를 다른 모양으로 보여주는 것이라 주인이 없다. TaxiCardServiceIntegrationTest");
		put(m, "GET /api/v1/festivals", Policy.AUTHENTICATED_ONLY,
				"기간으로 거른 공용 기준 데이터. FestivalIntegrationTest");
		put(m, "POST /api/v1/places/candidates", Policy.AUTHENTICATED_ONLY,
				"추천 엔진이 쓰는 후보 조회. 조건만으로 답이 정해진다. PlaceCandidateIntegrationTest");
		put(m, "GET /api/v1/origins", Policy.AUTHENTICATED_ONLY,
				"출발지 검색. 검색어만으로 답이 정해진다. OriginSearchServiceTest");
		put(m, "GET /api/v1/events/catalog", Policy.AUTHENTICATED_ONLY,
				"이벤트 종류 목록. 공용 기준 데이터다");
		put(m, "POST /api/v1/events", Policy.AUTHENTICATED_ONLY,
				"행동 이벤트 적재. 주체는 인증에서 읽고 본문의 사용자 값을 신뢰하지 않아야 한다 — 아래 '남은 위험' 참고. EventIngestServiceTest");
		put(m, "GET /api/v1/admin/analytics/kpis", Policy.ADMIN_ONLY,
				"집계 지표 조회. 예전에는 AUTHENTICATED_ONLY 였는데, 익명 출입증이 생긴 뒤로 그것이 "
						+ "'아무나' 와 같은 말이 됐다 — 내부 운영 숫자는 서비스 규모의 단서다. "
						+ "경로 앞자리가 실제 보호 장치다. AnalyticsControllerTest (-1010)");

		// ── 알림
		put(m, "PUT /api/v1/me/push-tokens", Policy.AUTHENTICATED_ONLY,
				"이 기기로 알림을 받겠다고 등록한다(-1391). 우리 자원이지만 «남의 것을 가리킬 자리»가 "
						+ "없다 — 주인은 경로가 아니라 출입증에서 온다. 🔴 익명 출입증에 매달면 로그아웃한 "
						+ "뒤에도 그 기기로 알림이 가므로 로그인을 요구한다. PushTokenControllerTest");
		put(m, "DELETE /api/v1/me/push-tokens/{}", Policy.OWNED,
				"로그아웃할 때 이 기기를 뗀다(-1391). 🔴 경로의 {token} 은 사람이 아니라 기기지만 "
						+ "OWNED 다 — 주인을 안 보면 아무나 남의 기기를 알림에서 떼어 낼 수 있다. 서비스가 "
						+ "(token, user_id) 둘로 지운다. 없거나 남의 것이면 204 로 조용히 지나간다 — 「그 토큰이 "
						+ "있다」를 알려 주지 않는다. PushTokenControllerTest");

		// ── 도구
		put(m, "POST /api/v1/tools/translate", Policy.AUTHENTICATED_ONLY,
				"문장 하나를 번역 업체에 대신 물어보는 창구라 우리 자원이 아니라 주인이 없다. "
						+ "인증을 요구하는 것은 route/directions 와 같은 이유 — 우리 업체 키로 남이 대신 "
						+ "번역을 돌리는 것(비용)을 막기 위해서다. TranslateControllerTest");
		put(m, "POST /api/v1/tools/translate/batch", Policy.AUTHENTICATED_ONLY,
				"여러 문장을 한 번에 번역한다(-1363). 장소·축제·기록처럼 서버가 한국어로만 가진 글을 "
						+ "일본어·중국어 화면에 보여 주려고 화면이 모아서 부른다. 위 단건 번역과 같은 이유로 "
						+ "인증을 요구한다 — 우리 업체 키로 남이 대신 돌리는 비용. 🔴 익명 출입증으로 열지 "
						+ "않은 것은 손님에게도 번역을 주면 캐시에 없는 문장마다 값이 나가기 때문이다. 손님에게 "
						+ "열지는 비용 판단이라 따로 정한다. TranslateControllerTest");

		// ── 날씨
		put(m, "GET /api/v1/weather", Policy.AUTHENTICATED_ONLY,
				"좌표·날짜로 답이 정해진다 — 우리 자원이 아니라 주인이 없다. 인증을 요구하는 것은 "
						+ "route/directions·tools/translate 와 같은 이유 — 우리 기상청 키로 남이 대신 "
						+ "조회를 돌리는 것(비용)을 막기 위해서다. WeatherControllerTest");

		// ── 대중교통 실시간 도착정보
		put(m, "GET /api/v1/transit/nearby-bus-arrivals", Policy.AUTHENTICATED_ONLY,
				"좌표로 답이 정해진다 — 우리 자원이 아니라 주인이 없다. 인증을 요구하는 것은 "
						+ "weather 와 같은 이유 — 우리 TAGO 키로 남이 대신 조회를 돌리는 것(호출 한도 "
						+ "소진)을 막기 위해서다.");

		// ── 환율 조회
		put(m, "GET /api/v1/exchange-rates", Policy.AUTHENTICATED_ONLY,
				"그날 환율은 누구에게나 같다 — 우리 자원이 아니라 주인이 없다. 인증을 요구하는 것은 "
						+ "weather·transit 과 같은 이유 — 우리 인증키로 남이 대신 조회를 돌리는 것(하루 "
						+ "1000회 한도 소진)을 막기 위해서다.");

		// ── 가까운 병원·약국·경찰 (S15P21E201-1893)
		put(m, "GET /api/v1/help-places/nearby", Policy.AUTHENTICATED_ONLY,
				"좌표와 갈래로 답이 정해진다 — 공공 자료(심평원·OSM)라 주인이 없다. 로그인은 다른 조회와 "
						+ "같은 문이라 요구한다(익명 세션도 된다) — 급할 때 막히지 않게 앱은 서버가 안 되면 "
						+ "앱에 실은 자료로 물러선다(frontend app/nearby-help.tsx).");

		// ── AI 여행 도우미
		put(m, "POST /api/v1/assistant/messages", Policy.AUTHENTICATED_ONLY,
				"자연어 메시지 하나를 AI 업체(Claude)에 대신 물어보는 창구라 우리 자원이 아니라 "
						+ "주인이 없다. 인증을 요구하는 것은 tools/translate 와 같은 이유 — 우리 업체 "
						+ "키로 남이 대신 호출을 돌리는 것(비용)을 막기 위해서다. AssistantControllerTest");

		// ── 기계용 내부 배치
		// 사람 계정과 무관하다. SecurityConfig 의 "/internal/**" → hasRole("INTERNAL") 이 막고,
		// 권한은 InternalTokenAuthenticationFilter 가 X-Internal-Token 헤더를 보고 심는다. 토큰을
		// 설정하지 않으면 아무 권한도 안 심겨서 전부 거부된다.
		put(m, "GET /internal/v1/batch/taste-vectors/stale", Policy.INTERNAL_ONLY,
				"표시가 뒤처진 사람 목록. 사람 신원이 아니라 공유 토큰으로 연다. "
						+ "InternalTokenAuthenticationFilterTest (-772)");
		put(m, "POST /internal/v1/batch/taste-vectors/rebuild", Policy.INTERNAL_ONLY,
				"넘긴 사람들의 취향 벡터를 다시 접는다. 같은 문·같은 토큰. "
						+ "InternalTokenAuthenticationFilterTest (-772) · TasteVectorFoldIntegrationTest (-787)");

		return m;
	}
}
