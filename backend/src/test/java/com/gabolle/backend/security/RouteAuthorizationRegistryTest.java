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
 * S15P21E201-672 — <b>모든 경로가 인가 정책을 명시하고 있는지</b>를 재는 검사.
 *
 * <h2>🔴 이 테스트가 막는 것</h2>
 * 인가는 엔드포인트를 <b>하나 빠뜨리면</b> 뚫린다. 그런데 빠뜨렸다는 사실은 아무 테스트도
 * 빨갛게 만들지 않는다 — 없는 검사는 실패하지 않기 때문이다. 이 저장소가 실제로 그랬다.
 * 여행·일정 API 가 {@code X-User-Id} 헤더로 사용자를 정해 인가가 우회되던 것(`-607`·`-610`)은
 * 사람이 손으로 찔러 보다 발견했고, 그때까지 어떤 테스트도 그것을 잡지 않았다.
 *
 * <p>그래서 이 테스트는 기능을 재지 않고 <b>빠뜨림을 재는다.</b> 컨트롤러의 모든 경로를 클래스
 * 경로에서 열거해 아래 {@code POLICY} 표와 대조하고, 표에 없는 경로가 하나라도 있으면
 * 실패한다. 새 엔드포인트를 만든 사람은 <b>그 경로의 인가 정책과 근거를 여기에 적어야</b>
 * 빌드를 통과할 수 있다.
 *
 * <h2>왜 Spring 컨텍스트를 띄우지 않는가</h2>
 * 컨트롤러 대부분에 {@code @Profile({"db","dev"})} 가 붙어 있어서, 프로필과 DB 없이 컨텍스트를
 * 띄우면 그 경로들이 <b>매핑에 아예 나타나지 않는다.</b> 그러면 이 검사가 "빠진 것이 없다" 고
 * 말하면서 실제로는 절반을 안 본 상태가 된다 — <b>있는 것만 세는 검사는 없는 것을 못 잡는다.</b>
 * 그래서 클래스 경로를 직접 훑어 애너테이션을 읽는다. DB 도 프로필도 필요 없고, 프로필 조건과
 * 무관하게 전부 보인다.
 *
 * <h2>정책 일곱 가지</h2>
 * <ul>
 *   <li>{@code PRE_AUTH} — 로그인 <b>전에</b> 부르는 인증 흐름 자체. 열려 있는 것이 정상이고
 *       보호는 안쪽에 있다(연속 실패 잠금, 1회용 티켓, 비밀번호 확인)</li>
 *   <li>{@code PUBLIC_TOKEN} — 로그인 없이 열려 있고, <b>추측 불가능한 표·키를 아는 사람만</b>
 *       실제로 무언가를 받는다</li>
 *   <li>{@code OWNED} — 요청자가 그 자원의 주인(또는 편집 권한자)이어야 한다.
 *       <b>남의 것을 부르면 2xx 가 나오면 안 된다</b></li>
 *   <li>{@code OTHER_USER_OK} — 남의 자원을 보는 것이 <b>기능 자체</b>다(프로필 보기, 팔로우).
 *       여기서 위험은 거부되지 않는 것이 아니라 <b>보여선 안 될 것이 섞이는 것</b>이다</li>
 *   <li>{@code AUTHENTICATED_ONLY} — 로그인만 하면 누구나 같은 답을 받는다. 자원에 주인이 없다</li>
 *   <li>🔴 {@code INTERNAL_ONLY} — <b>사람이 아니라 기계</b>가 부른다(Airflow 배치). 위 여섯은
 *       전부 "요청자가 누구인가" 를 묻는데, 이것은 요청자에게 신원이 <b>없다</b> —
 *       공유 토큰 하나({@code X-Internal-Token})가 전부다. {@code ADMIN_ONLY} 로 분류하면
 *       안 된다: 운영자 권한은 배포 설정의 이메일 목록에서 매 기동 계산되므로, 배치를 거기
 *       끼우면 <b>목록을 고치는 사람이 자기가 배치를 멈춘다는 것을 모른 채 멈춘다</b>
 *       (S15P21E201-772). 문이 다르다는 것이 이 정책의 존재 이유다</li>
 *   <li>🔴 {@code ADMIN_ONLY} — 운영자만. 자원의 주인이 <b>요청자가 아닌</b> 유일한 갈래다.
 *       나머지 다섯은 "내 것인가" 를 묻는데 이것은 "너는 운영자인가" 를 묻는다. 그래서
 *       {@code OWNED} 로 분류하면 안 된다 — 남의 것을 다루는 것이 기능이기 때문이다</li>
 * </ul>
 *
 * <p>🔴 이 표는 <b>정책이 실제로 지켜지는지</b>를 재지 않는다. 그건 표의 두 번째 칸이 가리키는
 * 테스트들이 재고, 이 검사는 "어느 경로도 정책 없이 존재하지 않는다" 하나만 본다. 둘을 섞으면
 * 이 파일이 거대한 통합 테스트가 되어 아무도 안 고친다.
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

		// 🔴 개수를 박아 두는 이유 — 인증 없이 열린 경로가 <b>조용히</b> 늘어나는 것이 이
		//    시스템에서 가장 비싼 실수다. 숫자를 박아 두면 하나 열 때마다 이 줄을 고치게 되고,
		//    그 변경이 diff 에 남아 리뷰에서 보인다. 실제로 이 저장소의 SecurityConfig 주석은
		//    "/oauth/ 아래 한 마디짜리 경로가 전부 열린다" 는 함정을 적어 두고 있다 — 두 마디로
		//    두지 않으면 새 경로가 의도 없이 열린다.
		// 🔴 18 → 19 (2026-09-11, S15P21E201-833). 늘어난 하나는
		//    POST /api/v1/auth/oauth/apple/form-post 다 — 애플이 form_post 로 결과를 되돌려 보내는
		//    자리라 로그인 전에 애플 서버가 직접 부른다. 열어도 되는 근거는 그 경로가 판정을
		//    하지 않는다는 것이다: 받은 값을 설정에 박힌 화면 주소로만 옮기고, state·nonce
		//    검증은 화면이 이어서 부르는 코드 교환이 그대로 한다.
		assertThat(open).hasSize(19);

		// 표를 아는 사람이 실제로 열린 것과 대조할 수 있게 목록도 고정한다
		assertThat(routesWith(Policy.PUBLIC_TOKEN)).containsExactlyInAnyOrder(
				"GET /api/v1/shares/{}",
				"GET /api/v1/uploads/images/{}");
	}

	@Test
	@DisplayName("🔴 운영자 경로가 모두 /api/v1/admin/ 아래에 있다 — 경로 규칙 하나로 막기 때문이다")
	void adminRoutesLiveUnderTheAdminPrefix() {
		// 🔴 이 저장소는 메서드 보안(@EnableMethodSecurity)이 꺼져 있어서 @PreAuthorize 가
		//    조용히 무시된다. 그래서 운영자 인가는 SecurityConfig 의
		//    "/api/v1/admin/**" → hasRole("ADMIN") 경로 규칙 하나가 전부 담당한다.
		//    그 아래에 없는 운영자 경로는 <b>아무도 막지 않는다.</b>
		assertThat(routesWith(Policy.ADMIN_ONLY))
				.isNotEmpty()
				.allSatisfy(route -> assertThat(route)
						.contains(" /api/v1/admin/"));
	}

	@Test
	@DisplayName("주인 검사가 필요한 경로가 절반을 넘는다 — 이 시스템의 기본은 소유 자원이다")
	void ownedRoutesAreTheMajority() {
		// 이 확인은 숫자 자체가 목적이 아니라, 누군가 정책을 대충 AUTHENTICATED_ONLY 로
		// 몰아넣는 것을 눈에 띄게 하려는 것이다. 소유 자원을 그렇게 분류하면 남의 것을
		// 거부하는지 아무도 안 재게 된다.
		// S15P21E201-343 -- 진짜 주인 없는 자원(번역 중계, route/directions 와 같은 이유)을 하나 더
		// 더해 31 대 31 로 동률이 됐다. 정책을 대충 몰아넣은 신호가 아니라 자연스러운 성장이라 >= 로
		// 완화한다 -- 여러 개가 한꺼번에 AUTHENTICATED_ONLY 로 넘어가는 진짜 몰아넣기는 여전히 잡는다.
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
	 * <h3>🔴 Spring 의 컴포넌트 스캐너를 쓰지 않는다</h3>
	 * {@code ClassPathScanningCandidateComponentProvider} 는 찾은 후보에 <b>조건부 애너테이션을
	 * 평가</b>한다. {@code @Profile} 도 조건이므로, 활성 프로필이 없는 환경에서 그것을 쓰면
	 * {@code @Profile({"db","dev"})} 가 붙은 컨트롤러가 <b>전부 걸러진다.</b> 그러면 이 검사가
	 * 경로 0개를 찾고도 "빠진 것이 없다" 며 초록이 된다 — 이 클래스가 막으려는 것과 정확히 같은
	 * 종류의 침묵이다.
	 *
	 * <p>처음 이 파일을 그렇게 만들었고 실제로 그렇게 통과했다. 그래서 조건 평가를 지나지 않는
	 * 방식으로 바꿨다 — 클래스 파일을 직접 찾아 리플렉션으로 애너테이션만 읽는다.
	 */
	private static Set<String> discoverRoutes() {
		Set<String> routes = new TreeSet<>();
		for (Class<?> controller : scanForControllers()) {
			routes.addAll(routesOf(controller));
		}
		// 🔴 하나도 못 찾았으면 그것 자체가 실패다. 이 메서드가 조용히 빈 집합을 돌려주면
		//    위의 모든 확인이 무의미하게 초록이 된다.
		if (routes.isEmpty()) {
			throw new IllegalStateException(
					"컨트롤러를 하나도 못 찾았다 — 이 검사가 아무것도 보지 않고 있다는 뜻이다");
		}
		return routes;
	}

	/**
	 * 같은 패키지의 {@link SecurityAllowlistMatchesRoutesTest} 가 쓰는 창구.
	 *
	 * <p>경로 열거를 두 곳에 복사하면 한쪽만 고쳐지는 날이 온다. 열거 방식이 이 파일의
	 * 관심사이므로 여기서만 만들고 빌려 준다.
	 */
	/** 같은 패키지의 허용 목록 검사가 쓰는 창구 — 로그인 전에 부르는 경로만. */
	static Set<String> preAuthRoutesForAudit() {
		return routesWith(Policy.PRE_AUTH);
	}

	/**
	 * 로그인 없이 열려야 하는 경로 전부 — {@link Policy#PRE_AUTH} 와 {@link Policy#PUBLIC_TOKEN}.
	 *
	 * <p>🔴 {@link #preAuthRoutesForAudit()} 와 갈라 두는 이유. 저쪽은 "빠지면 기능이 죽는다"
	 * 를 보는 창구이고(로그인 전에 반드시 열려 있어야 한다), 이쪽은 "이 밖의 것이 열려 있으면
	 * 구멍이다" 를 보는 창구다. {@code PUBLIC_TOKEN} 은 로그인은 없지만 <b>표 자체가
	 * 자격증명</b>이라 앞쪽 목록에 넣으면 안 되고, 뒤쪽 목록에서는 빠지면 안 된다.
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
	 * 한 컨트롤러가 선언한 경로들.
	 *
	 * <p>🔴 {@code discoverRoutes} 에서 이 자리를 떼어낸 이유는 <b>회귀 검사가 직접 부를 자리가
	 * 필요해서</b>다 — S15P21E201-836. 그 전에는 경로를 읽는 방식이 클래스 경로 스캔 안에만
	 * 있어서, {@code path=} 로 쓴 매핑을 감사가 보는지 확인하려면 실제 컨트롤러를 하나 만들어야
	 * 했다. 그런데 {@code @RestController} 를 붙인 순간 그것이 감사 대상에 들어가 표와 열린 경로
	 * 수까지 건드린다. 이제 검사가 {@code @RestController} 없는 대역 클래스를 만들어 이 메서드만
	 * 부를 수 있다.
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
	 * 🔴 {@code value} 와 {@code path} 를 <b>둘 다</b> 본다 — S15P21E201-836.
	 *
	 * <p>스프링에게 이 둘은 완전히 같은 별칭이다({@code @AliasFor}). 그런데 애너테이션 객체를
	 * 직접 읽는 이 감사에게는 다르다 — {@code @PostMapping(path = "/x")} 로 쓰면 {@code value()}
	 * 가 비어 있고, 그때 이 감사는 그 메서드의 경로를 <b>클래스 경로 그대로로 오인했다.</b>
	 *
	 * <p>그것이 왜 위험한가. 오인한 이름이 표에 없으면 "정책 없는 경로" 로 빨개지지만,
	 * <b>클래스 경로가 이미 표에 있으면 조용히 통과한다.</b> 즉 {@code @RequestMapping("/api/v1/auth")}
	 * 처럼 표에 있는 컨트롤러에 {@code path=} 로 새 경로를 더하면, 정책을 한 줄도 안 적고
	 * 이 검사를 지나간다 — 이 검사가 막으려는 것이 정확히 그것이다.
	 *
	 * <p>실제로 겪었다. {@code S15P21E201-833} 에서 애플 착지 경로를 {@code path=} 로 썼고,
	 * 감사는 그것을 {@code POST /api/v1/auth/oauth/apple} 로 봤다. 그때는 그 이름이 표에 없어
	 * 빨개졌지만, 한 마디만 달랐으면 아무 일도 안 일어났을 것이다.
	 *
	 * <h2>🔴 실측 — 구멍은 메서드 쪽에만 있었다</h2>
	 * 부수기 실험으로 확인했다. 이 메서드를 {@code return value} 로 되돌리면
	 * {@code RouteDiscoveryReadsPathAttributeTest} 의 <b>메서드 매핑 검사 둘만</b> 빨개지고
	 * 클래스 레벨 검사는 그대로 초록이다.
	 *
	 * <p>이유는 읽는 방식이 다르기 때문이다. 클래스 경로는
	 * {@code AnnotatedElementUtils.findMergedAnnotation} 으로 읽는데 그쪽은 {@code @AliasFor} 를
	 * 실제로 합쳐 준다. 메서드 매핑은 {@code method.getAnnotation(...)} — 순수 반사라 별칭을
	 * 합치지 않고 선언된 값 그대로다. <b>같은 별칭인데 읽는 도구가 달라서 결과가 갈렸다.</b>
	 *
	 * <p>그래서 {@link #classLevelPath} 쪽 호출은 지금도 필요하지 않다. 그래도 남겨 두는 이유는
	 * 두 자리가 같은 규칙으로 보이는 편이 다음 사람에게 안전하고, 읽는 도구를 나중에 바꿔도
	 * 결과가 안 흔들리기 때문이다.
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
	 * 경로 변수 이름을 지운다 — {@code {tripId}} 든 {@code {id}} 든 같은 자리다.
	 *
	 * <p>이름까지 표에 적으면 변수 이름만 바꿔도 이 테스트가 빨개진다. 그건 인가와 무관한
	 * 변경이라 잡을 이유가 없다.
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

		// ── 인증 흐름 — 로그인 전에 부른다 ─────────────────────────────────────────
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

		// ── 인증 흐름 — 로그인 상태에서 부른다 ────────────────────────────────────
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
		put(m, "POST /api/v1/menu-scans", Policy.AUTHENTICATED_ONLY,
				"메뉴판 사진을 서버가 모델에 중계한다. 우리 자원이 아니라 주인이 없다 — 인증을 요구하는 것은 "
						+ "tools/translate 와 같은 이유(우리 키로 남이 호출을 돌리는 비용)에 더해, "
						+ "한도를 사람 단위로 세야 하기 때문이다. 사진은 저장하지 않는다. MenuScanControllerTest (-1025)");

		// ── 컬렉션 (-1013) ──────────────────────────────────────────────────────
		// 여덟 경로가 같은 근거를 공유한다 — 경로에 남의 번호를 넣을 자리가 없고(/me),
		// 컬렉션은 언제나 주인과 함께 찾는다(findByIdAndUserId). 없는 것과 남의 것을
		// 같은 404 로 답해 존재 자체를 안 흘린다. CollectionControllerTest
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

		put(m, "GET /api/v1/me/preferences/spend", Policy.OWNED,
				"계정 기본 씀씀이 성향 조회(-709). 대상이 경로에 없고 인증 주체로만 정해진다 — 남의 것을 지정할 방법이 없다. SpendProfileControllerTest");
		put(m, "PUT /api/v1/me/preferences/spend", Policy.OWNED,
				"위와 같다. SpendProfileControllerTest");
		put(m, "GET /api/v1/me/preferences/taste", Policy.OWNED,
				"계정 기본 취향 조회(-639). 온보딩 첫 실행과 마이페이지가 읽는다. 위 /spend 와 같은 근거로 OWNED — "
				+ "대상이 경로에 없고 인증 주체로만 정해진다. TastePreferencesControllerTest");
		put(m, "PUT /api/v1/me/preferences/taste", Policy.OWNED,
				"위와 같다. 🔴 보낸 차원만 바뀌고 안 보낸 것은 남는다 — 지우기는 answerStatus=UNKNOWN 으로 온다. "
				+ "TastePreferencesControllerTest");

		// ── 여행 ────────────────────────────────────────────────────────────────
		put(m, "POST /api/v1/trips", Policy.AUTHENTICATED_ONLY,
				"새로 만드는 것이라 기존 자원의 주인 개념이 없다. 소유자는 인증 주체로 박힌다. "
				+ "S15P21E201-317 — 익명 세션(ROLE_ANONYMOUS)도 이 자리만은 통과한다. 만든 사람이 곧 "
				+ "소유자가 되므로 익명이라도 남의 것을 건드릴 수 없다 — AuthenticatedUsers.requireOwner");
		// 🔴 목록은 OWNED 가 아니라 AUTHENTICATED_ONLY 다 — 부를 때 자원을 지목하지 않기
		//    때문이다. 위험은 "남의 것을 부르면 거부되는가" 가 아니라 "남의 여행이 목록에
		//    섞이는가" 이고, 그것은 저장소가 참여 표로 거른다. TripListIntegrationTest 의
		//    doesNotLeakTripsIAmNotAMemberOf 가 그 자리를 지킨다.
		put(m, "GET /api/v1/trips", Policy.AUTHENTICATED_ONLY,
				"내 여행 목록. 참여 표로 걸러 남의 여행이 섞이지 않는다. TripListIntegrationTest");
		put(m, "GET /api/v1/trips/{}", Policy.OWNED,
				"비회원은 존재를 감춘 404. TripControllerGetTest · ItineraryAccessIntegrationTest");
		put(m, "DELETE /api/v1/trips/{}", Policy.OWNED,
				"삭제는 OWNER 만. 동행자는 403, 비회원과 없는 여행은 같은 404. TripDeleteIntegrationTest");
		put(m, "PUT /api/v1/trips/{}/title", Policy.OWNED,
				"이름은 OWNER·EDITOR 만 바꾼다. 보기 전용 동행자가 바꾸면 만든 사람의 목록에서 "
						+ "자기 여행이 다른 이름으로 보인다 — VIEWER 는 403, 비회원과 없는 여행은 "
						+ "같은 404. TripTitleTest (-1023)");
		put(m, "GET /api/v1/trips/{}/itineraries", Policy.OWNED,
				"참여자만. 비회원과 없는 여행이 같은 404. TripItineraryListIntegrationTest");
		put(m, "GET /api/v1/trips/{}/stories", Policy.OWNED,
				"참여자만. 비회원과 없는 여행이 같은 404 이고, 참여자에게도 그 기록의 공개 범위 판정"
						+ "(StoryVisibilityPolicy.canView)을 한 번 더 지난다 — 여행에 달렸다는 이유로 남의 "
						+ "나만 보기 기록이 새면 -137 에서 막은 구멍이 다시 열린다. "
						+ "TripStoryJourneyFunctionalTest (-829)");
		put(m, "GET /api/v1/trips/{}/activity", Policy.OWNED,
				"참여자만. TripActivityIntegrationTest");
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

		// ── 초대·공유 ────────────────────────────────────────────────────────────
		put(m, "POST /api/v1/trip-invites/{}/accept", Policy.AUTHENTICATED_ONLY,
				"표를 아는 로그인 사용자가 참여자가 되는 것이 기능이다. 보호는 43글자 난수 표와 7일 만료다. TripInviteIntegrationTest");
		put(m, "GET /api/v1/shares/{}", Policy.PUBLIC_TOKEN,
				"로그인 없이 열린다. 표가 43글자 난수이고 개인정보 칸이 응답 record 에 아예 없다 (-332). ShareLinkIntegrationTest");
		put(m, "POST /api/v1/shares/{}/clone", Policy.AUTHENTICATED_ONLY,
				"표를 아는 로그인 사용자가 자기 여행으로 복제하는 것이 기능이다. 원본은 안 바뀐다. ShareCloneIntegrationTest");

		// ── 일정 ────────────────────────────────────────────────────────────────
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
		put(m, "PUT /api/v1/itineraries/{}/items/{}/actual", Policy.OWNED,
				"그 여행의 편집자만 자기 일정의 방문 시각을 적는다 — 남의 여행은 존재를 감춘 404, VIEWER 는 403. ItineraryActualTimeIntegrationTest (-293)");
		put(m, "GET /api/v1/itineraries/{}/days/{}/pace", Policy.OWNED,
				"지연 경고 조회다. 보기만 하므로 VIEWER 도 본다 — 참여자가 아니면 존재를 감춘 404. ItineraryPaceIntegrationTest (-304)");
		put(m, "GET /api/v1/itineraries/{}/rhythm", Policy.OWNED,
				"여행 리듬 요약 조회다. 위와 같은 이유로 VIEWER 도 본다. ItineraryPaceIntegrationTest (-308)");
		put(m, "POST /api/v1/itineraries/{}/days/{}/replan", Policy.OWNED,
				"남은 하루 재계획은 판을 만드는 편집이라 편집 권한자만. ItineraryReplanIntegrationTest (-308)");

		// ── 추천 작업 ────────────────────────────────────────────────────────────
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

		// ── 코스 테마 ────────────────────────────────────────────────────────────
		put(m, "GET /api/v1/course-categories", Policy.AUTHENTICATED_ONLY,
				"자원에 주인이 없는 목록 조회다. 쓰이는 자리가 여행 만들기 안이라 로그인 뒤에 둔다. "
						+ "CourseThemeContractFunctionalTest (-450)");

		// ── 기록·피드·사용자 ─────────────────────────────────────────────────────
		put(m, "POST /api/v1/stories", Policy.AUTHENTICATED_ONLY,
				"새로 쓰는 것이라 주인 개념이 없다. 작성자는 인증 주체로 박힌다");
		put(m, "GET /api/v1/stories", Policy.AUTHENTICATED_ONLY,
				"내가 볼 수 있는 것만 나오는 목록이다. 공개 범위 판정이 canView 다. StoryFeedIntegrationTest");
		put(m, "GET /api/v1/stories/{}", Policy.OTHER_USER_OK,
				"남의 기록을 보는 것이 기능이다. PRIVATE 는 작성자 아닌 사람에게 404. StoryCrudIntegrationTest");
		put(m, "PATCH /api/v1/stories/{}", Policy.OWNED,
				"수정은 작성자만 — requireAuthor. StoryCrudIntegrationTest");
		put(m, "DELETE /api/v1/stories/{}", Policy.OWNED,
				"삭제는 작성자만 — requireAuthor. StoryCrudIntegrationTest");
		// ── 기록 공동 작성 (-770) ────────────────────────────────────────────────
		// 🔴 다섯 경로 전부 AUTHENTICATED_ONLY 다 — 자원 주인 검사(OWNED)로 분류하지 않는다.
		//    각 경로 안에서 "만든 사람만"·"볼 수 있는 사람만" 을 실제로 가르는 것은
		//    StoryCoauthorService(StoryService.requireVisible · StoryVisibilityPolicy.isParticipant
		//    재사용)이고, 이 표는 그 판정이 존재한다는 사실만 기록한다. 로그인만 하면 누구나
		//    부를 수 있는 자리(참여자 목록·초대 수락)와 실제로는 만든 사람만 통과하는 자리
		//    (초대 발급·동행자 편입·제거)가 섞여 있지만, 다섯 다 "경로 자체에 남의 것과 내 것을
		//    가르는 별도의 자원 식별자가 없다" 는 공통점으로 여기 둔다 — 여행 쪽 5절의
		//    TripCollaborationController 항목들이 전부 OWNED 로 분류된 것과 다른 점이다. 그쪽은
		//    tripId 각각이 이미 "그 여행의 누구인가" 를 묻지만, 이쪽은 storyId 자체가 이미
		//    StoryService.requireVisible 을 지나야만 얻어지는 404/403 판정 뒤에 있다.
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

		put(m, "GET /api/v1/feed/home", Policy.AUTHENTICATED_ONLY,
				"내 피드다. 대상이 인증 주체로만 정해진다. FeedControllerTest");
		put(m, "GET /api/v1/feed/community", Policy.AUTHENTICATED_ONLY,
				"공개 기록만 모은 목록이다. FeedControllerTest");
		put(m, "GET /api/v1/users/{}/profile", Policy.OTHER_USER_OK,
				"남의 프로필 보기가 기능이다. 위험은 비공개 항목이 섞이는 것. FollowIntegrationTest");
		put(m, "GET /api/v1/users/{}/stories", Policy.OTHER_USER_OK,
				"남의 기록 목록 보기가 기능이다. visibleScopesOf 가 팔로우 여부로 범위를 가른다. FollowIntegrationTest");
		put(m, "PUT /api/v1/users/{}/block", Policy.OTHER_USER_OK,
				"남을 차단하는 것이 기능이다. 주체는 인증에서만 읽어 남의 이름으로 차단할 수 없다. BlockIntegrationTest");
		put(m, "DELETE /api/v1/users/{}/block", Policy.OTHER_USER_OK,
				"차단 해제도 같다. 내가 건 차단만 풀 수 있다 — 지우는 키가 (나, 상대) 쌍이다. BlockIntegrationTest");
		put(m, "PUT /api/v1/users/{}/follow", Policy.OTHER_USER_OK,
				"남을 팔로우하는 것이 기능이다. 주체는 인증에서만 읽어 남의 이름으로 팔로우할 수 없다. FollowIntegrationTest");
		put(m, "DELETE /api/v1/users/{}/follow", Policy.OTHER_USER_OK,
				"언팔로우도 같다. 주체는 인증에서만 읽는다. FollowIntegrationTest");

		// ── 신고와 검토 (-254 · -267) ────────────────────────────────────────────
		put(m, "POST /api/v1/stories/{}/reports", Policy.OTHER_USER_OK,
				"남의 기록에 신고를 거는 것이 기능이다. 안 보이는 기록은 존재를 감춘 404. 중복 신고는 조용히 성공한다(남의 신고 여부를 흘리지 않기 위해). StoryReportFilingIntegrationTest");
		put(m, "GET /api/v1/admin/story-reports", Policy.ADMIN_ONLY,
				"검토 큐. SecurityConfig 의 /api/v1/admin/** → hasRole(ADMIN) 이 막는다. AdminModerationAuthorizationIntegrationTest");
		put(m, "POST /api/v1/admin/story-reports/{}/remove", Policy.ADMIN_ONLY,
				"운영자 삭제. 같은 경로 규칙이 막는다. AdminModerationQueueIntegrationTest");
		put(m, "POST /api/v1/admin/story-reports/{}/dismiss", Policy.ADMIN_ONLY,
				"운영자 기각. 같은 경로 규칙이 막는다. AdminModerationQueueIntegrationTest");

		// ── 방문 인증과 리뷰 (-279 · -287 · -408) ─────────────────────────────────
		put(m, "POST /api/v1/places/{}/visit-verifications", Policy.AUTHENTICATED_ONLY,
				"주체를 인증에서만 읽고 좌표는 저장하지 않으므로 남의 인증을 대신 만들 자리가 없다. VisitVerificationIntegrationTest");
		put(m, "POST /api/v1/places/{}/reviews", Policy.AUTHENTICATED_ONLY,
				"본문에 사용자도 인증 여부도 받지 않는다 — 서버가 인증 기록을 조회해 정하므로 남의 리뷰를 쓸 수 없다. PlaceReviewIntegrationTest");
		put(m, "GET /api/v1/places/{}/reviews", Policy.AUTHENTICATED_ONLY,
				"장소 하나의 목록이라 주인이 없다. 인증·미인증을 다 보여주고 평균은 인증된 것만으로 낸다. PlaceReviewIntegrationTest");

		// ── 업로드 ──────────────────────────────────────────────────────────────
		put(m, "POST /api/v1/uploads/story-image", Policy.AUTHENTICATED_ONLY,
				"새로 올리는 것이라 주인 개념이 없다. 올린 사람은 인증 주체로 박히고, 남이 올린 주소를 자기 기록에 붙이면 400 이다. ImageUploadIntegrationTest");
		put(m, "GET /api/v1/uploads/images/{}", Policy.PUBLIC_TOKEN,
				"피드 화면이 <img> 로 부르고 그 요청에는 Authorization 이 안 붙는다. 키가 UUID 라 추측 불가. ImageUploadIntegrationTest");

		// ── 장소·기준 데이터 ─────────────────────────────────────────────────────
		put(m, "GET /api/v1/places", Policy.AUTHENTICATED_ONLY,
				"장소는 공용 기준 데이터라 사용자별로 답이 다르지 않다. PlaceSearchIntegrationTest");
		put(m, "GET /api/v1/places/facets", Policy.AUTHENTICATED_ONLY,
				"갈래별 건수. 공용 기준 데이터다. PlaceFacetInterestTagIntegrationTest");
		put(m, "GET /api/v1/places/categories", Policy.AUTHENTICATED_ONLY,
				"적재된 place.category 값과 건수. 요청자와 무관한 공용 기준 데이터라 facets 와 같은 정책이다. PlaceCategoryServiceTest");
		put(m, "GET /api/v1/routes/directions", Policy.AUTHENTICATED_ONLY,
				"좌표 두 개로 답이 정해진다 — 우리 자원이 아니라 주인이 없다. 인증을 요구하는 것은 "
						+ "우리 카카오 키로 남이 길찾기를 대신 쓰는 것을 막기 위해서다. RouteControllerTest");
		put(m, "GET /api/v1/places/nearby", Policy.AUTHENTICATED_ONLY,
				"좌표만으로 답이 정해진다 — 컨트롤러 주석이 그렇게 적고 있다. NearbyFacetAndRadiusTest");
		put(m, "GET /api/v1/places/accommodations", Policy.AUTHENTICATED_ONLY,
				"숙소 후보 조회(S15P21E201-456). category 로 거른 공용 기준 데이터라 요청자별로 답이 갈리지 않는다. AccommodationQueryIntegrationTest");
		put(m, "GET /api/v1/places/{}", Policy.AUTHENTICATED_ONLY,
				"공용 기준 데이터. 다만 itineraryInclusion 은 요청자별로 갈리므로 그 자리는 인증 주체로만 읽는다. PlaceDetailIntegrationTest");
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

		// ── 도구 (-343) ──────────────────────────────────────────────────────────
		put(m, "POST /api/v1/tools/translate", Policy.AUTHENTICATED_ONLY,
				"문장 하나를 번역 업체에 대신 물어보는 창구라 우리 자원이 아니라 주인이 없다. "
						+ "인증을 요구하는 것은 route/directions 와 같은 이유 — 우리 업체 키로 남이 대신 "
						+ "번역을 돌리는 것(비용)을 막기 위해서다. TranslateControllerTest");

		// ── 날씨 (-366) ──────────────────────────────────────────────────────────
		put(m, "GET /api/v1/weather", Policy.AUTHENTICATED_ONLY,
				"좌표·날짜로 답이 정해진다 — 우리 자원이 아니라 주인이 없다. 인증을 요구하는 것은 "
						+ "route/directions·tools/translate 와 같은 이유 — 우리 기상청 키로 남이 대신 "
						+ "조회를 돌리는 것(비용)을 막기 위해서다. WeatherControllerTest");

		// ── AI 여행 도우미 (-802) ────────────────────────────────────────────────
		put(m, "POST /api/v1/assistant/messages", Policy.AUTHENTICATED_ONLY,
				"자연어 메시지 하나를 AI 업체(Claude)에 대신 물어보는 창구라 우리 자원이 아니라 "
						+ "주인이 없다. 인증을 요구하는 것은 tools/translate 와 같은 이유 — 우리 업체 "
						+ "키로 남이 대신 호출을 돌리는 것(비용)을 막기 위해서다. AssistantControllerTest");

		// ── 기계용 내부 배치 (S15P21E201-772 · -787) ──────────────────────────────
		//
		// 🔴 사람 계정과 무관하다. SecurityConfig 의 "/internal/**" → hasRole("INTERNAL") 이
		//    막고, 권한은 InternalTokenAuthenticationFilter 가 X-Internal-Token 헤더를 보고
		//    심는다. 토큰을 설정하지 않으면 아무 권한도 안 심겨서 전부 거부된다 —
		//    "설정을 깜빡했더니 열려 있었다" 가 되지 않는다.
		put(m, "GET /internal/v1/batch/taste-vectors/stale", Policy.INTERNAL_ONLY,
				"표시가 뒤처진 사람 목록. 사람 신원이 아니라 공유 토큰으로 연다. "
						+ "InternalTokenAuthenticationFilterTest (-772)");
		put(m, "POST /internal/v1/batch/taste-vectors/rebuild", Policy.INTERNAL_ONLY,
				"넘긴 사람들의 취향 벡터를 다시 접는다. 같은 문·같은 토큰. "
						+ "InternalTokenAuthenticationFilterTest (-772) · TasteVectorFoldIntegrationTest (-787)");

		return m;
	}
}
