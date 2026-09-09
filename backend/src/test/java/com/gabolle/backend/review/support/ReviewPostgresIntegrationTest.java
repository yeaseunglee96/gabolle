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
 * <p>{@link TestDatabase}·{@link PostgresAvailableCondition} 은 추천 쪽(S15P21E201-543)이 만든
 * 것을 그대로 읽어 쓴다 — 도메인마다 복사하면 한쪽만 고쳐진다.
 *
 * <p>{@code ddl-auto} 는 {@code validate} 다. 장소·인증 슬라이스와 같은 이유로, 엔티티가 실제
 * 표와 어긋난 것을 배포가 아니라 여기서 잡는다 — 이 티켓의 표(place_visit_verification ·
 * place_review)는 감독자가 만들었고 엔티티는 이 작업이 새로 짰으므로 어긋날 여지가 있다.
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
