package com.gabolle.backend.batch.support;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.testslice.BatchSliceApplication;

/**
 * 진짜 PostgreSQL 위에서 도는 배치 테스트의 밑바탕.
 *
 * <p>H2 로 대신하지 않는다. 이 배치가 지키려는 것이 DB 만 지킬 수 있는 약속이다.
 * <ul>
 * <li>조건부 UNIQUE 색인 {@code uq_user_taste_vector_current} — "현재 판은 하나"</li>
 * <li>{@code ck_user_taste_vector_observed_until} — 행동을 봤다면 표시가 있다</li>
 * <li>{@code ck_user_taste_weight_interaction_has_support}</li>
 * <li>{@code TIMESTAMPTZ} 비교와 JSONB 읽기</li>
 * </ul>
 *
 * <p>DB 를 못 구하면 건너뜀으로 표시된다 — 조용히 통과하지 않는다.
 *
 * <p>판 번호를 테스트에서 박아 넣는다. 운영 값을 그대로 쓰면 운영 설정을 고치는 날
 * 여기가 같이 빨개지는데, 그 둘은 상관이 없다.
 */
@SpringBootTest(classes = BatchSliceApplication.class,
		properties = { "spring.profiles.active=db", "spring.jpa.hibernate.ddl-auto=none",
				"spring.flyway.enabled=true", "gabolle.taste-vector.vector-version=test-fold-v1",
				"gabolle.taste-vector.ontology-version=test-ontology-v1" })
@ExtendWith(PostgresAvailableCondition.class)
public abstract class BatchPostgresTest {

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
	}
}
