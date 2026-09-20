package com.gabolle.backend.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.ResourcePropertySource;

/**
 * {@code application-dev.properties} 가 배포 환경에서 제대로 읽히는지 본다.
 *
 * <p>이 파일은 배포 프로필의 정본이다({@code SPRING_PROFILES_ACTIVE=dev}). 여기서
 * {@code ${VAR}} 하나가 안 풀리면 앱은 그 속성과 아무 상관 없는 이유로 기동에 실패하고,
 * 값 하나가 잘못 읽히면 그 값이 조용히 틀린 채로 운영에 나간다.
 *
 * <p>주입되는 환경변수의 목록은 {@code backend/Jenkinsfile} 의 Deploy 단계가 정본이다.
 * 거기서 하나를 지우면 이 테스트가 먼저 깨진다.
 */
class DevProfilePlaceholderTest {

	/** Jenkinsfile Deploy 단계가 항상 넘기는 값. */
	private static Map<String, Object> deployBaseline() {
		Map<String, Object> env = new LinkedHashMap<>();
		env.put("GABOLLE_DB_URL", "jdbc:postgresql://postgres:5432/app_db");
		env.put("GABOLLE_DB_USERNAME", "app_user");
		env.put("GABOLLE_DB_PASSWORD", "test-password");
		env.put("GABOLLE_JWT_SECRET", "0123456789abcdef0123456789abcdef");
		// 추천 응답의 서비스 버전과 배포 환경. 값 자체가 아니라 채워진다는 사실만 필요하다.
		env.put("GABOLLE_SERVICE_VERSION", "0123456789abcdef0123456789abcdef01234567");
		env.put("GABOLLE_DEPLOYMENT_ENVIRONMENT", "dev");
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

	/**
	 * 발신자 이름이 메일함에서 깨져 나간 회귀를 막는다.
	 *
	 * <p>{@code .properties} 는 Java 규격상 ISO-8859-1 로 읽힌다. UTF-8 로 저장한 한글을
	 * 그대로 넣으면 바이트가 잘못 해석돼 발신자 이름이 깨진 채 메일이 나간다.
	 * Spring 이 읽는 방식과 같게 읽어서 값을 대조한다.
	 *
	 * <p>기대값을 유니코드 이스케이프로 적는다 — 이 테스트 파일 자체의 인코딩에 기대지
	 * 않기 위한 것이다.
	 */
	@Test
	void senderNameSurvivesThePropertiesFileEncoding() throws IOException {
		StandardEnvironment environment = new StandardEnvironment();
		environment.getPropertySources().addLast(
				new ResourcePropertySource(new ClassPathResource("application-dev.properties")));

		String resolved = environment.resolveRequiredPlaceholders(
				environment.getProperty("gabolle.mail.from-name", ""));

		assertThat(resolved).isEqualTo("가볼래");
	}

	/**
	 * 같은 실수가 다른 속성에서 반복되는 것을 막는다.
	 *
	 * <p>파싱된 값이 아니라 파일 바이트를 본다. {@code Properties} 는 읽는 시점에 유니코드
	 * 이스케이프를 실제 문자로 바꿔 주므로, 파싱한 뒤에는 올바르게 적은 한글과 바이트가
	 * 잘못 해석된 한글을 구분할 수 없다.
	 *
	 * <p>주석은 읽을 때 무시되므로 검사에서 뺀다.
	 */
	@Test
	void noPropertyLineCarriesRawNonAsciiBytes() throws IOException {
		byte[] bytes;
		try (InputStream in = new ClassPathResource("application-dev.properties").getInputStream()) {
			bytes = in.readAllBytes();
		}

		List<String> offenders = new ArrayList<>();
		String[] lines = new String(bytes, StandardCharsets.ISO_8859_1).split("\n");
		for (int index = 0; index < lines.length; index++) {
			String line = lines[index].replace("\r", "");
			String trimmed = line.trim();
			if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) {
				continue;
			}
			for (int i = 0; i < line.length(); i++) {
				if (line.charAt(i) > 0x7F) {
					offenders.add((index + 1) + "행: " + line);
					break;
				}
			}
		}
		assertThat(offenders).isEmpty();
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
