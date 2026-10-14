package com.gabolle.backend.facility.support;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.testslice.FacilitySliceApplication;

/**
 * 진짜 PostgreSQL 위에서 도는 근처 편의시설 통합 테스트의 밑바탕 (S15P21E201-2052). Docker 가 없으면
 * {@link PostgresAvailableCondition} 이 건너뛰고, 접속 주소는 {@link TestDatabase} 가 준다 — 장소 쪽({@code PlacePostgresIntegrationTest})
 * 과 같은 방식이다.
 *
 * <p>{@code ddl-auto} 는 {@code validate} 다 — 엔티티가 마이그레이션이 만든 표와 어긋나면 컨텍스트가 뜨는 것 자체가 실패한다.
 * Flyway 가 전체 마이그레이션을 적용하므로 {@code V20261014120000__facility.sql} 의 제약(CHECK·유일)이 그대로 걸린다.
 */
@SpringBootTest(classes = FacilitySliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.flyway.enabled=true"
})
@ExtendWith(PostgresAvailableCondition.class)
public abstract class FacilityPostgresIntegrationTest {

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
	}
}
