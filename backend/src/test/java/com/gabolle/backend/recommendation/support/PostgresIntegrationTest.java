package com.gabolle.backend.recommendation.support;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;

import com.gabolle.testslice.RecommendationSliceApplication;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 진짜 PostgreSQL 위에서 도는 통합 테스트의 공통 밑바탕. H2 로 대신하지 않는다 — 여기서
 * 저장하는 JSONB · UUID · {@code VARCHAR[]} 의 계약은 PostgreSQL 에서만 검증된다.
 *
 * DB 를 어디서 가져오는지는 {@link TestDatabase} 가 정하고, 둘 다 없으면 이 클래스는 건너뛴
 * 것으로 표시된다.
 *
 * {@code spring.profiles.active=db} 하나가 두 가지를 한다 — 기본 프로필 {@code no-db}
 * (DataSource · JPA · Flyway 를 일부러 제외)가 적용되지 않게 하고, 동시에
 * {@code @Profile({"db","dev"})} 가 붙은 추천·Outbox 빈을 만든다.
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
