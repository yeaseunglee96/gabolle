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
 * AuthController 가 내주기로 한 POST 경로가 실제로 붙어 있는지 본다.
 *
 * <p>매핑이 없으면 404 가 아니라 401 이 나간다. Spring 이 처리기를 못 찾아 {@code /error} 로
 * 넘기는데 그 경로는 SecurityConfig 의 공개 목록에 없어 인증을 요구하고, 앱은 401 을 전부
 * 비밀번호 오류로 바꿔 보여주므로 화면만 봐서는 경로가 사라진 것을 알 수 없다.
 *
 * <p>경로가 붙어 있는지만 본다. 무엇을 돌려주는지는 다른 검사가 본다.
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
