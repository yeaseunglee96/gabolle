package com.gabolle.backend.place.support;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.testslice.PlaceSliceApplication;

/**
 * 진짜 PostgreSQL 위에서 도는 장소 통합 테스트의 밑바탕.
 *
 * <p>{@link TestDatabase} 와 {@link PostgresAvailableCondition} 은 추천 쪽(S15P21E201-543)이 만든
 * 것을 <b>그대로 읽어서</b> 쓴다. 같은 일을 하는 도구를 도메인마다 복사하면 한쪽만 고쳐지고,
 * 그때부터 "어느 쪽이 진짜 DB 를 보는가" 에 답이 둘이 된다.
 *
 * <h2>🔴 {@code ddl-auto} 를 {@code none} 이 아니라 {@code validate} 로 둔 이유</h2>
 *
 * 기존 통합 테스트들은 {@code none} 이다. 그러면 <b>엔티티 매핑이 실제 표와 어긋나도 테스트가
 * 통과한다.</b> 어긋남은 배포에서 {@code dev} 프로필이 {@code validate} 를 돌릴 때 처음 드러나고,
 * 그때는 애플리케이션이 아예 안 뜬다 — 이 저장소는 그런 이유로 이미 세 번 502 를 냈다.
 *
 * <p>{@code place} 는 표를 우리가 만들지 않고 이미 있는 것에 <b>맞춰</b> 매핑하는 도메인이라
 * 어긋날 여지가 특히 크다. 그래서 그 검사를 배포가 아니라 여기서 한다. 슬라이스가 장소 엔티티만
 * 올리므로 검사 범위도 딱 그만큼이다.
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
