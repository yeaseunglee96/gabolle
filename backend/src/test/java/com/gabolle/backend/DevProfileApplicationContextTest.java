package com.gabolle.backend;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;

/**
 * 실제 배포가 쓰는 프로필(dev)로 전체 애플리케이션이 뜨는지 본다. 다른 통합 테스트는 슬라이스 앱만
 * 띄우고 {@link GabolleBackendApplicationTests} 는 {@code @Profile({"db","dev"})} 빈을 아예 만들지
 * 않으므로, "각자는 맞는데 합치면 안 뜬다" 는 배선 문제는 여기서만 잡힌다.
 *
 * <p>{@link #SCHEMA} 이름을 접속 사용자 이름({@code gabolle})과 같게 만들면 안 된다. PostgreSQL 기본
 * {@code search_path} 가 {@code "$user", public} 이라, 사용자와 같은 이름의 schema 가 생기는 순간 그
 * 뒤에 여는 모든 연결이 그쪽을 보게 되어 다른 테스트의 Flyway 가 표를 엉뚱한 데 만든다. 끝나고 지우는
 * 것으로는 못 고친다 — Spring 이 컨텍스트를 캐시해 JVM 이 끝날 때까지 안 닫으므로, 살아 있는 컨텍스트가
 * 쓰던 표가 사라진다.
 */
@SpringBootTest(properties = {
		"spring.profiles.active=dev",
		"GABOLLE_DB_SCHEMA=devprofile",
		"spring.mail.host=127.0.0.1",
		"gabolle.auth.jwt-secret=0123456789abcdef0123456789abcdef"
})
@ExtendWith(PostgresAvailableCondition.class)
class DevProfileApplicationContextTest {

	private static final String SCHEMA = "devprofile";

	@DynamicPropertySource
	static void datasource(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
	}

	/**
	 * {@code spring.flyway.create-schemas=false} 라 운영에서 DB 관리자가 미리 하는 일을 대신한다.
	 * Flyway 가 스키마를 기대하는 컨텍스트 리프레시보다 먼저 돌아야 한다. 남겨 둬도 접속 사용자
	 * 이름과 다르니 무해하다.
	 */
	@BeforeAll
	static void createSchema() throws Exception {
		try (Connection connection = DriverManager.getConnection(
				TestDatabase.url(), TestDatabase.username(), TestDatabase.password());
				Statement statement = connection.createStatement()) {
			statement.execute("CREATE SCHEMA IF NOT EXISTS " + SCHEMA);
		}
	}

	@Test
	void contextLoads() {
	}

	/**
	 * 엔진 빈이 빠져도 앱은 정상으로 뜨고 로그도 안 남는데 모든 추천 요청이 실패하므로, 컨텍스트가
	 * 뜨는 것만으로는 부족하다. 기동 검사기에 기대지 않는다 — 그쪽은 엔진과 같은 조건을 써서 함께 빠진다.
	 */
	@Test
	void 추천엔진이배선된다(@org.springframework.beans.factory.annotation.Autowired
			org.springframework.context.ApplicationContext context) {
		String[] engines = context.getBeanNamesForType(
				com.gabolle.backend.recommendation.adapter.RecommendationEnginePort.class);

		org.assertj.core.api.Assertions.assertThat(engines)
				.as("dev 프로필 전체 앱에 추천 엔진이 없다 — 이대로 배포하면 앱은 뜨고 "
						+ "모든 추천만 조용히 실패한다")
				.isNotEmpty();
	}
}
