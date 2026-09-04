package com.gabolle.backend.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.ResourcePropertySource;

/**
 * {@code application-dev.properties} 의 자리표시자가 배포 환경에서 전부 풀리는지 본다.
 *
 * <p>이 파일은 배포 프로필의 정본이고({@code SPRING_PROFILES_ACTIVE=dev}), 여기서
 * {@code ${VAR}} 하나가 안 풀리면 앱은 그 속성과 아무 상관 없는 이유로 기동에 실패한다.
 * 2026-09-03 에 운영 backend 가 세 번 기동에 실패했고 전부 이 계열이었다.
 *
 * <p>주입되는 값의 목록은 {@code backend/Jenkinsfile} 의 Deploy 단계가 정본이다.
 * 거기서 환경변수를 하나 지우면 이 테스트가 먼저 깨진다.
 */
class DevProfilePlaceholderTest {

	/** Jenkinsfile Deploy 단계가 항상 넘기는 값. */
	private static Map<String, Object> deployBaseline() {
		Map<String, Object> env = new LinkedHashMap<>();
		env.put("GABOLLE_DB_URL", "jdbc:postgresql://postgres:5432/app_db");
		env.put("GABOLLE_DB_USERNAME", "app_user");
		env.put("GABOLLE_DB_PASSWORD", "test-password");
		env.put("GABOLLE_JWT_SECRET", "0123456789abcdef0123456789abcdef");
		return env;
	}

	@Test
	void everyPlaceholderResolvesWithTheEnvironmentJenkinsProvides() throws IOException {
		Map<String, Object> env = deployBaseline();
		env.put("GABOLLE_MAIL_ENABLED", "true");
		env.put("GABOLLE_MAIL_USERNAME", "gabolle@example.test");
		env.put("GABOLLE_MAIL_PASSWORD", "app-password");

		assertThat(unresolvedKeys(env)).isEmpty();
	}

	@Test
	void everyPlaceholderStillResolvesWhenMailCredentialsAreAbsent() throws IOException {
		// Jenkins Credentials 에 메일 계정을 넣기 전 상태다. 이때도 앱은 떠야 한다 —
		// 메일만 못 보내는 것과 서버가 아예 안 뜨는 것은 전혀 다른 사고다.
		assertThat(unresolvedKeys(deployBaseline())).isEmpty();
	}

	private Map<String, String> unresolvedKeys(Map<String, Object> environmentVariables) throws IOException {
		StandardEnvironment environment = new StandardEnvironment();
		environment.getPropertySources()
				.addFirst(new MapPropertySource("deploy-env", environmentVariables));
		ResourcePropertySource devProperties =
				new ResourcePropertySource(new ClassPathResource("application-dev.properties"));
		environment.getPropertySources().addLast(devProperties);

		Map<String, String> unresolved = new HashMap<>();
		for (String key : devProperties.getPropertyNames()) {
			Object raw = devProperties.getProperty(key);
			if (raw == null) {
				continue;
			}
			try {
				environment.resolveRequiredPlaceholders(raw.toString());
			}
			catch (IllegalArgumentException ex) {
				unresolved.put(key, ex.getMessage());
			}
		}
		return unresolved;
	}
}
