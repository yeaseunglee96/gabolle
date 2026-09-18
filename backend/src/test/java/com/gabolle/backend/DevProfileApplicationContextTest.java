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
 * 실제 배포가 쓰는 프로필(dev)로 <b>전체</b> 애플리케이션이 뜨는지 본다 — S15P21E201-161
 * 배포 실패(2026-09-08) 후속.
 *
 * <h2>🔴 이 테스트가 메우는 구멍 — 왜 CI 는 초록인데 배포는 죽었나</h2>
 *
 * {@code RouteOptimizerAvailabilityCheck}(S15P21E201-161)가
 * {@code com.fasterxml.jackson.databind.ObjectMapper}(Jackson 2, 옛 패키지)를 import 했다.
 * 컴파일은 통과했지만 — 이 저장소는 Jackson 3({@code tools.jackson})이라 Spring 은
 * 그 타입의 빈을 등록하지 않는다. 배포({@code SPRING_PROFILES_ACTIVE=dev})에서만
 * "No qualifying bean of type ObjectMapper" 로 죽었다.
 *
 * <p>CI({@code backend:build})가 이걸 못 잡은 이유를 찾다가 이 저장소의 구조적 구멍을
 * 발견했다 — {@link GabolleBackendApplicationTests}(전체 앱)는 {@code no-db} 프로필로 돌아
 * {@code @Profile({"db","dev"})}(이 저장소에 123개 클래스가 이 조건을 쓴다)인
 * 빈을 애초에 만들지 않고, DB 가 필요한 통합 테스트(예: {@code AccountDeletionIntegrationTest})는
 * 전부 <b>슬라이스 앱</b>({@code AuthSliceApplication} 등 — 도메인 하나만 골라 띄우는 작은
 * 앱)만 쓴다. <b>전체 앱을 진짜 DB 로 띄우는 테스트가 이 저장소에 하나도 없었다</b> —
 * S15P21E201-546(스키마 버그가 187개 테스트를 전부 초록으로 통과시킴)과 뿌리가 같은 종류의
 * 간극이다.
 *
 * <p>여기서는 딱 하나만 확인한다 — {@code dev} 프로필의 컨텍스트가 <b>뜨는가</b>. 기능이
 * 맞는지는 슬라이스 통합 테스트들의 몫이다. 이 테스트가 잡는 것은 "각자는 맞는데 합치면
 * 안 뜬다" 는 배선 문제뿐이다 — 정확히 오늘 겪은 그 종류다.
 *
 * <p>🔴 {@code gabolle.auth.jwt-secret} 처럼 기본값이 없는 값은 여기서 직접 채운다 — 실제
 * {@code GABOLLE_JWT_SECRET} 환경변수가 있어야 하는 게 아니라, {@code spring.datasource.*}
 * 처럼 이 값이 채워진다는 사실만 필요하다({@link TestDatabase} 와 같은 판단).
 *
 * <h2>🔴 스키마는 왜 여기서 직접 만드나 — CI 에서 실제로 겪은 두 번째 실수</h2>
 *
 * 이 클래스를 처음 커밋했을 때 로컬에서는 통과했는데 CI 에서 또 실패했다 —
 * {@code FlywaySqlScriptException}("schema \"gabolle\" does not exist"). 이유는
 * {@code application-dev.properties} 의 {@code spring.flyway.create-schemas=false} 다 —
 * 그 파일 자체가 "운영에서는 이 schema 를 DB 관리자가 먼저 만들고 app_user 에게 권한을
 * 준다" 고 적어 뒀다. 로컬 검증 때는 내가 직접 {@code CREATE SCHEMA} 를 손으로 미리 돌려
 * 뒀던 것이라, CI 의 매번 새로 뜨는 Postgres 에는 그 스키마가 없다는 것을 놓쳤다.
 *
 * <p>이 테스트에서는 그 "DB 관리자" 역할을 {@link #createSchema()} 가 대신한다 —
 * {@code create-schemas=false} 를 우회하지 않는다(실제 배포 설정을 그대로 존중하는 것이
 * 이 테스트의 존재 이유다). Flyway 가 스키마 존재를 기대하는 시점(컨텍스트 리프레시)보다
 * 먼저(JUnit5 {@code @BeforeAll}) 돌게 해서, 운영에서 사람이 미리 하는 그 단계를
 * 흉내낸다.
 *
 * <h2>🔴 schema 이름을 {@code gabolle} 로 쓰지 않는 이유 — 세 번째 실수, 이번엔 남의 테스트를 깼다</h2>
 *
 * 처음엔 {@code gabolle} schema 를 만들고 끝나면 {@code DROP SCHEMA} 로 지우는 방식이었다.
 * 로컬·CI 양쪽에서 이 클래스 하나만 보면 통과했지만, 전체 스위트에서
 * {@code EventQualityGateTest}(S15P21E201-546)가 이 클래스 <b>뒤에</b> 도는 순서에서만
 * 간헐적으로 깨졌다 — 고지혁이 근본 원인을 찾았다(MR !425). PostgreSQL 의 기본
 * {@code search_path}는 {@code "$user", public}인데, 접속 사용자 이름이 정확히
 * {@code gabolle} 이다. {@code gabolle} 이라는 이름으로 schema 를 만드는 순간
 * {@code $user} 가 그쪽으로 풀리고, <b>그 뒤에 새로 여는 모든 연결</b>(dev 프로필인지와
 * 무관하게)에서 Flyway 가 표를 {@code public} 대신 {@code gabolle} 에 만든다.
 *
 * <p>🔴 <b>끝나고 지우는 것으로는 못 고친다는 것도 고지혁이 지적했다.</b> Spring 은 컨텍스트를
 * 캐시해서 JVM 이 끝날 때까지 안 닫는다 — {@code gabolle} schema 가 있는 동안 뜬 다른
 * 컨텍스트가 그 표를 계속 쓰는 채로 살아 있는데 {@code @AfterAll} 이
 * {@code DROP SCHEMA ... CASCADE} 를 하면, <b>살아 있는 컨텍스트가 쓰던 표가 사라진다</b>.
 * 게다가 지운 뒤에는 {@code $user} 가 다시 안 풀려 {@code public} 로 떨어지므로, 그
 * 컨텍스트보다 먼저 떠서 {@code public} 에 표를 만들어 둔 <b>또 다른</b> 컨텍스트가 있으면
 * 오류 없이 엉뚱한 표를 읽는다 — 실행 순서에 따라 갈리는, 원래 것보다 더 고약한 종류의
 * 플레이키다.
 *
 * <p>진짜 원인은 "schema 를 만든 것" 이 아니라 "<b>접속 사용자와 같은 이름으로</b> 만든
 * 것"이다. 이름만 다르면 {@code $user} 가 애초에 안 풀리므로 부작용 자체가 안 생기고,
 * 지울 필요도 없다 — {@code GABOLLE_DB_SCHEMA} 를 이 테스트 전용 이름으로 덮어써서
 * {@code application-dev.properties} 의 네 자리(Flyway 두 곳·Hibernate·Hikari)가 전부
 * 그쪽을 보게 한다.
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
	 * 운영에서 DB 관리자가 미리 하는 일을 대신한다({@code spring.flyway.create-schemas=false}
	 * — 클래스 주석의 두 번째 문단 참고). {@code gabolle} 이 아니라 {@link #SCHEMA} 를 쓰므로
	 * 끝나고 지울 필요가 없다 — 남아 있어도 접속 사용자 이름과 다르니 무해하다.
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
	 * 추천 엔진 배선 확인 — S15P21E201-808.
	 *
	 * <p>컨텍스트가 뜨는 것만으로는 부족하다. {@code BaselineRecommendationEngine} 이 빠져도
	 * 앱은 정상으로 뜨고 로그도 안 남는데, 모든 추천 요청이 {@code ENGINE_NOT_CONFIGURED} 로
	 * 실패한다. 장소 표가 비어 있던 동안에는 어차피 후보가 0건이라 이 결함이 가려져 있었다.
	 *
	 * <p>기동 검사기({@code BaselineEngineStartupValidator})에 기대지 않는다. 그쪽은 엔진과
	 * 같은 조건을 써서, 엔진이 빠지는 상황에서 검사기도 함께 빠진다.
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
