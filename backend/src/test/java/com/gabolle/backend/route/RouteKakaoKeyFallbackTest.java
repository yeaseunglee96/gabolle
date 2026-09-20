package com.gabolle.backend.route;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.ResourcePropertySource;

/**
 * 배포 프로필에서 카카오 키가 어디서 오는지 못 박는다.
 *
 * 길찾기와 장소 검색은 로그인에 쓰는 것과 같은 카카오 REST API 키를 쓴다 — 카카오가 앱 하나에
 * REST 키를 하나만 주기 때문이다. 그 물려받기는 설정 파일의 중첩 기본값 한 줄에 걸려 있고,
 * 그 줄이 조용히 깨져도 기동은 되고 경로도 계속 나온다. 다만 전부 추정으로 나온다.
 */
class RouteKakaoKeyFallbackTest {

	private static final String KAKAO_REST_KEY = "kakao-rest-key-from-oauth-credential";

	private static StandardEnvironment environmentWith(Map<String, Object> env) throws IOException {
		StandardEnvironment environment = new StandardEnvironment();
		environment.getPropertySources().addFirst(new MapPropertySource("deploy-env", env));
		environment.getPropertySources()
				.addLast(new ResourcePropertySource(new ClassPathResource("application-dev.properties")));
		return environment;
	}

	@Test
	@DisplayName("🔴 전용 키가 없으면 로그인용 카카오 키를 그대로 물려받는다 — 길찾기와 장소 검색 둘 다")
	void bothInheritTheOauthKakaoKeyWhenNoDedicatedKeyIsGiven() throws IOException {
		Map<String, Object> env = new LinkedHashMap<>();
		env.put("GABOLLE_KAKAO_CLIENT_ID", KAKAO_REST_KEY);

		StandardEnvironment environment = environmentWith(env);

		assertThat(environment.getProperty("gabolle.route.kakao-rest-api-key")).isEqualTo(KAKAO_REST_KEY);
		assertThat(environment.getProperty("gabolle.place.origin.kakao-rest-api-key")).isEqualTo(KAKAO_REST_KEY);
	}

	@Test
	@DisplayName("전용 키를 주면 그것이 이긴다 — 나중에 로그인 앱과 지도 앱을 나눌 수 있어야 한다")
	void dedicatedKeyWins() throws IOException {
		Map<String, Object> env = new LinkedHashMap<>();
		env.put("GABOLLE_KAKAO_CLIENT_ID", KAKAO_REST_KEY);
		env.put("GABOLLE_KAKAO_REST_API_KEY", "dedicated-map-key");

		StandardEnvironment environment = environmentWith(env);

		assertThat(environment.getProperty("gabolle.route.kakao-rest-api-key")).isEqualTo("dedicated-map-key");
	}

	@Test
	@DisplayName("둘 다 없어도 빈 값으로 풀린다 — 키가 없다고 기동이 멈추면 안 된다")
	void resolvesToEmptyWhenNothingIsGiven() throws IOException {
		StandardEnvironment environment = environmentWith(new LinkedHashMap<>());

		assertThat(environment.getProperty("gabolle.route.kakao-rest-api-key")).isEmpty();
	}
}
