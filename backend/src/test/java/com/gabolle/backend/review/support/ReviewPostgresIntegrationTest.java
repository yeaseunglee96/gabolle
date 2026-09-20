package com.gabolle.backend.review.support;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.testslice.ReviewSliceApplication;

/**
 * 진짜 PostgreSQL 위에서 도는 방문 인증·리뷰 통합 테스트의 밑바탕.
 *
 * {@code ddl-auto} 가 {@code validate} 인 것은 엔티티와 실제 표가 어긋난 것을 배포가 아니라
 * 여기서 잡기 위해서다.
 */
@SpringBootTest(classes = ReviewSliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.flyway.enabled=true"
})
@ExtendWith(PostgresAvailableCondition.class)
public abstract class ReviewPostgresIntegrationTest {

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
	}
}
