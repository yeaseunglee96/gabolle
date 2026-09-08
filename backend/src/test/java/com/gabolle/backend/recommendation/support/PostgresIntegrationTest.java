package com.gabolle.backend.recommendation.support;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;

import com.gabolle.testslice.RecommendationSliceApplication;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 진짜 PostgreSQL 위에서 도는 통합 테스트의 공통 밑바탕.
 *
 * <p>🔴 H2 로 대신하지 않는 이유: 이 티켓이 저장하는 것은 JSONB · UUID · {@code VARCHAR[]} 이고,
 * 그 계약은 PostgreSQL 에서만 진짜로 검증된다. H2 로 통과한 초록은 "PostgreSQL 에서 된다" 를
 * 전혀 뜻하지 않으면서 뜻하는 것처럼 보인다.
 *
 * <p>DB 를 어디서 가져오는지는 {@link TestDatabase} 가 정한다 — 밖에서 준 것이 있으면 그것을,
 * 없고 도커가 있으면 컨테이너를. 둘 다 없으면 이 클래스는 <b>건너뛴 것으로 표시</b>된다.
 *
 * <p>🔴 {@code spring.profiles.active=db} 하나가 두 가지를 한다. 기본 프로필
 * {@code no-db}(DataSource · JPA · Flyway 를 일부러 제외)가 적용되지 않게 하고, 동시에
 * {@code @Profile({"db","dev"})} 가 붙은 추천·Outbox 빈을 만든다. 인증(S15P21E201-312)이
 * 도입한 방식이고, 도메인마다 다른 스위치를 두지 않으려고 그대로 쓴다.
 */
@SpringBootTest(classes = RecommendationSliceApplication.class, properties = {
		// 인증이 도입한 프로필 방식에 맞춘다 — active 가 있으면 spring.profiles.default(no-db) 는 적용되지 않는다.
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.flyway.enabled=true",
		"gabolle.recommendation.service-version=test-service-0.0.1",
		"gabolle.recommendation.deployment-environment=test",
		"gabolle.recommendation.default-top-k=5"
})
@ExtendWith(PostgresAvailableCondition.class)
@Import(RecommendationTestConfiguration.class)
public abstract class PostgresIntegrationTest {

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
	}
}
