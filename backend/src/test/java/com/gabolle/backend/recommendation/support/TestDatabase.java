package com.gabolle.backend.recommendation.support;

import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * 통합 테스트가 쓸 진짜 PostgreSQL 을 잡는다. <b>두 가지 방법 중 되는 쪽</b>을 쓴다.
 *
 * <ol>
 * <li><b>밖에서 준 DB</b> — 환경변수 {@code GABOLLE_TEST_DB_URL}(+{@code _USERNAME},
 *     {@code _PASSWORD})이 있으면 그것을 쓴다. 도커가 없는 CI 러너나, 이미 PostgreSQL 이
 *     떠 있는 서버에서 이 길로 돈다.</li>
 * <li><b>Testcontainers</b> — 아무것도 안 줬고 도커가 있으면 컨테이너를 하나 띄운다.
 *     개발자 노트북의 기본 경로다.</li>
 * </ol>
 *
 * <p>🔴 <b>둘 다 없으면 테스트는 통과가 아니라 건너뜀으로 표시된다</b> ({@link #isAvailable()}).
 * 도커 없는 곳에서 초록을 보고 "DB 계약이 검증됐다" 고 믿는 것이 이 티켓에서 가장 위험한
 * 오독이다.
 *
 * <p>Testcontainers(**테스트가 시작될 때 진짜 DB 를 도커 컨테이너로 띄우고 끝나면 지우는
 * 라이브러리**) 컨테이너는 JVM 당 한 번만 띄우고 클래스마다 다시 띄우지 않는다. 정리는
 * Testcontainers 의 청소 컨테이너가 JVM 종료 시 처리한다.
 *
 * <h2>서버·CI 에서 쓰는 법</h2>
 *
 * <pre>
 * GABOLLE_TEST_DB_URL=jdbc:postgresql://localhost:5432/gabolle_test \
 * GABOLLE_TEST_DB_USERNAME=gabolle \
 * GABOLLE_TEST_DB_PASSWORD=... \
 * ./gradlew test
 * </pre>
 */
public final class TestDatabase {

	private static final String URL_KEY = "GABOLLE_TEST_DB_URL";

	private static final String USERNAME_KEY = "GABOLLE_TEST_DB_USERNAME";

	private static final String PASSWORD_KEY = "GABOLLE_TEST_DB_PASSWORD";

	private static PostgreSQLContainer<?> container;

	private static String url;

	private static String username;

	private static String password;

	private TestDatabase() {
	}

	/**
	 * JUnit 의 {@code @EnabledIf} 가 부른다. 거짓이면 그 테스트 클래스는 <b>건너뛴 것으로</b>
	 * 표시된다 — 조용히 통과하지 않는다.
	 */
	public static boolean isAvailable() {
		return externalUrl() != null || DockerClientFactory.instance().isDockerAvailable();
	}

	/**
	 * {@code @DynamicPropertySource} 가 그대로 쓸 수 있게 DataSource 설정을 한 자리에 모은다.
	 * 통합 테스트 밑바탕이 둘 이상이라 복사해 두면 한쪽만 고쳐지는 일이 생긴다.
	 */
	public static void registerDatasource(org.springframework.test.context.DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", TestDatabase::url);
		registry.add("spring.datasource.username", TestDatabase::username);
		registry.add("spring.datasource.password", TestDatabase::password);
		registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
		// 밖에서 준 DB 는 앞선 실행의 표가 남아 있을 수 있다. Flyway 가 스스로 정리하고
		// 다시 만들게 해서, 컨테이너를 쓸 때와 같은 상태에서 시작한다.
		registry.add("spring.flyway.clean-disabled", () -> "false");
		registry.add("spring.flyway.clean-on-validation-error", () -> "true");
	}

	public static synchronized String url() {
		ensureStarted();
		return url;
	}

	public static synchronized String username() {
		ensureStarted();
		return username;
	}

	public static synchronized String password() {
		ensureStarted();
		return password;
	}

	private static void ensureStarted() {
		if (url != null) {
			return;
		}
		String external = externalUrl();
		if (external != null) {
			verifyItIsAThrowawayDatabase(external);
			url = external;
			username = setting(USERNAME_KEY);
			password = setting(PASSWORD_KEY);
			return;
		}
		container = new PostgreSQLContainer<>("postgres:16-alpine");
		container.start();
		url = container.getJdbcUrl();
		username = container.getUsername();
		password = container.getPassword();
	}

	/**
	 * 🔴 이 테스트들은 표를 <b>지운다</b>. 팀 서버에는 다른 프로젝트의 PostgreSQL 이 이미 떠
	 * 있으므로, 실수로 그쪽을 가리키면 남의 데이터가 사라진다. 그래서 데이터베이스 이름에
	 * {@code test} 가 없으면 시작하지 않는다.
	 *
	 * <p>완전한 보호가 아니다 — 이름에 {@code test} 가 들어간 실제 DB 는 못 걸러낸다.
	 * 그래도 가장 흔한 실수(운영 URL 을 그대로 붙여넣기)는 여기서 멈춘다.
	 */
	private static void verifyItIsAThrowawayDatabase(String jdbcUrl) {
		String databaseName = jdbcUrl;
		int lastSlash = databaseName.lastIndexOf('/');
		if (lastSlash >= 0) {
			databaseName = databaseName.substring(lastSlash + 1);
		}
		int query = databaseName.indexOf('?');
		if (query >= 0) {
			databaseName = databaseName.substring(0, query);
		}
		if (!databaseName.toLowerCase().contains("test")) {
			throw new IllegalStateException(
					"이 테스트는 표를 지운다. " + URL_KEY + " 의 데이터베이스 이름에 'test' 가 없어 중단한다: "
							+ databaseName + " — 버려도 되는 DB 를 따로 만들어 가리키십시오");
		}
	}

	private static String externalUrl() {
		String value = setting(URL_KEY);
		return (value == null || value.isBlank()) ? null : value;
	}

	/** 환경변수를 먼저 보고, 없으면 같은 이름의 시스템 프로퍼티({@code -D})를 본다. */
	private static String setting(String key) {
		String value = System.getenv(key);
		return (value != null) ? value : System.getProperty(key);
	}
}
