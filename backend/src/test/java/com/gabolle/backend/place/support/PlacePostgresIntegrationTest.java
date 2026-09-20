package com.gabolle.backend.place.support;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.testslice.PlaceSliceApplication;

/**
 * 진짜 PostgreSQL 위에서 도는 장소 통합 테스트의 밑바탕. Docker 가 없으면
 * {@link PostgresAvailableCondition} 이 건너뛰고, 접속 주소는 {@link TestDatabase} 가 준다 —
 * 둘 다 추천 쪽이 만든 것을 그대로 쓴다.
 *
 * <p>{@code ddl-auto} 는 다른 통합 테스트와 달리 {@code validate} 다. {@code place} 는 이미 있는
 * 표에 맞춰 매핑하는 도메인이라 엔티티가 어긋날 여지가 크고, {@code none} 이면 그 어긋남이
 * 배포에서 처음 드러난다. 이 클래스를 상속하면 컨텍스트가 뜨는 것 자체가 그 검사가 된다.
 */
@SpringBootTest(classes = PlaceSliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.flyway.enabled=true"
})
@ExtendWith(PostgresAvailableCondition.class)
public abstract class PlacePostgresIntegrationTest {

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
	}
}
