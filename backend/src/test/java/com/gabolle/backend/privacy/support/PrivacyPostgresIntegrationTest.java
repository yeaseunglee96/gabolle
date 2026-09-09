package com.gabolle.backend.privacy.support;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.testslice.PrivacySliceApplication;

/**
 * 진짜 PostgreSQL 위에서 도는 개인정보 자동 정리 배치 통합 테스트의 밑바탕.
 *
 * <p>{@code AuthPostgresIntegrationTest} 와 같은 이유로 진짜 DB 가 필요하다 — 이 배치는 JPQL
 * 벌크 삭제와 {@code ON DELETE CASCADE} 상호작용(auth_session → auth_refresh_token) 이 핵심이라,
 * 그 상호작용은 실제 제약이 걸린 표에서만 드러난다.
 */
@SpringBootTest(classes = PrivacySliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.flyway.enabled=true",
		"gabolle.auth.jwt-secret=test-only-secret-value-at-least-32-chars-long",
		"gabolle.mail.enabled=false",
		"gabolle.privacy.cleanup.session-grace-days=1",
		"gabolle.privacy.cleanup.event-retention-days=1"
})
@ExtendWith(PostgresAvailableCondition.class)
public abstract class PrivacyPostgresIntegrationTest {

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
	}
}
