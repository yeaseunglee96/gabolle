package com.gabolle.backend.auth.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * AuthController 가 내주기로 한 POST 경로가 실제로 붙어 있는지 본다 - S15P21E201-689 회귀 방지.
 *
 * <p>2026-09-07 에 -689 가 소셜 인증을 세 갈래(로그인·가입 필요·연결 필요)로 고치면서
 * {@code /oauth/{provider}/challenge} · {@code /web/refresh} · {@code /web/logout} 세 매핑을 함께
 * 지웠다. 컴파일도 됐고 다른 테스트도 전부 통과했다 - 지워진 것을 부르는 테스트가 하나도 없었기
 * 때문이다. 배포된 앱에서 소셜 로그인 버튼을 눌러야 드러났다.
 *
 * <p>🔴 매핑이 없으면 404 가 아니라 <b>401</b> 이 나간다. Spring 이 처리기를 못 찾아 {@code /error}
 * 로 넘기는데 그 경로는 SecurityConfig 의 공개 목록에 없어서 인증을 요구한다. 앱은 401 을 전부
 * "이메일 또는 비밀번호가 올바르지 않아요" 로 바꿔 보여주므로 화면만 봐서는 경로가 사라진 것을
 * 알 수 없다. 그래서 이 검사가 값을 한다 - 지워지는 순간 배포가 아니라 여기서 먼저 빨개진다.
 *
 * <p>경로가 붙어 있는지만 본다. 그 경로가 무엇을 돌려주는지는 다른 테스트들이 본다.
 */
class AuthControllerRoutesPresentTest {

	@DisplayName("AuthController 에 붙어 있어야 하는 POST 경로")
	@ParameterizedTest(name = "POST {0}")
	@ValueSource(strings = {
			"/oauth/{provider}/challenge",
			"/web/refresh",
			"/web/logout",
			"/oauth/{provider}",
			"/oauth/signup",
			"/oauth/link",
			"/login",
			"/signup",
			"/refresh",
			"/logout"})
	void postMappingExists(String path) {
		assertThat(declaredPostPaths()).contains(path);
	}

	/** 클래스에 직접 적힌 {@code @PostMapping} 의 값을 전부 모은다. Spring 을 띄우지 않는다. */
	private static List<String> declaredPostPaths() {
		return Arrays.stream(AuthController.class.getDeclaredMethods())
				.map(method -> method.getAnnotation(PostMapping.class))
				.filter(mapping -> mapping != null)
				.flatMap(mapping -> Stream.of(mapping.value()))
				.toList();
	}
}
