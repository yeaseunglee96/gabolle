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
 * 배포 프로필에서 카카오 키가 어디서 오는지 못 박는다 — S15P21E201-184.
 *
 * <h2>🔴 왜 이 검사가 필요한가</h2>
 *
 * 길찾기와 장소 검색은 <b>로그인에 쓰는 것과 같은 카카오 REST API 키</b>를 쓴다. 카카오가
 * 앱 하나에 REST 키를 하나만 주기 때문이다. 그래서 새 자격증명을 만들지 않고
 * {@code GABOLLE_KAKAO_CLIENT_ID} 를 물려받게 해 두었는데, 그 물려받기가 <b>설정 파일의
 * 중첩 기본값 한 줄</b>에 걸려 있다.
 *
 * <p>그 한 줄이 조용히 깨지면 무슨 일이 나는지가 문제다 — 기동은 그대로 되고, 경로는
 * 계속 나오고, 다만 <b>전부 추정으로</b> 나온다. 아무도 안 죽으니 아무도 모른다.
 * {@code DevProfilePlaceholderTest} 는 "안 풀린 자리가 없다" 까지만 보므로 이 어긋남을
 * 못 잡는다. 그래서 값이 실제로 물려받아지는지를 여기서 본다.
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
