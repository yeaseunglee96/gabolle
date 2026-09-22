package com.gabolle.backend.auth.support;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.testslice.AuthSliceApplication;

/**
 * 진짜 PostgreSQL 위에서 도는 인증 통합 테스트의 밑바탕.
 *
 * <p>{@link TestDatabase} 와 {@link PostgresAvailableCondition} 은 추천 쪽이 만든 것을 그대로 읽어
 * 쓴다. 같은 일을 하는 도구를 도메인마다 복사하면 한쪽만 고쳐진다.
 *
 * <p>{@code GABOLLE_JWT_SECRET} 자리를 채워야 한다. {@code AuthStartupValidator} 가 32자 미만이거나
 * 기본값이면 기동을 일부러 막는다. 여기 넣는 값은 테스트 전용이다.
 *
 * <p>{@code ddl-auto} 는 {@code validate} 다. 장소 슬라이스와 같은 이유로, 엔티티가 실제 표와 어긋난
 * 것을 배포가 아니라 여기서 잡는다.
 */
@SpringBootTest(classes = AuthSliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.flyway.enabled=true",
		"gabolle.auth.jwt-secret=test-only-secret-value-at-least-32-chars-long",
		"gabolle.mail.enabled=false"
})
@ExtendWith(PostgresAvailableCondition.class)
public abstract class AuthPostgresIntegrationTest {

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
	}
}
