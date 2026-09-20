package com.gabolle.backend.feed.support;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.testslice.FeedSliceApplication;

/**
 * 진짜 PostgreSQL 위에서 도는 피드 테스트의 밑바탕.
 *
 * <p>H2 로 대신하지 않는다. 여기서 지키려는 것이 조건부 UNIQUE 색인,
 * {@code cardinality(reason_codes) > 0}, {@code jsonb_typeof(payload) = 'object'},
 * {@code VARCHAR[]} 와 JSONB 매핑처럼 H2 에 없는 것들이라, H2 로 통과한 초록은
 * PostgreSQL 에서 된다는 뜻이 전혀 아니면서 그런 것처럼 보인다.
 *
 * <p>DB 를 못 구하면 {@code PostgresAvailableCondition} 이 건너뜀으로 표시한다.
 * 조용히 통과하지 않는다.
 */
@SpringBootTest(classes = FeedSliceApplication.class,
		properties = { "spring.profiles.active=db", "spring.jpa.hibernate.ddl-auto=none",
				"spring.flyway.enabled=true" })
@ExtendWith(PostgresAvailableCondition.class)
public abstract class FeedPostgresTest {

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
	}
}
