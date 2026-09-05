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
 * <p>🔴 H2 로 대신하지 않는다. 이 티켓이 실제로 지키려는 것이 <b>DB 만 지킬 수 있는
 * 약속</b>들이기 때문이다.
 * <ul>
 * <li>조건부 UNIQUE 색인 — "READY 세대는 한 사용자·한 화면에 하나" (원자적 교체의 핵심)</li>
 * <li>{@code cardinality(reason_codes) > 0} — "이유 없는 추천은 저장할 수 없다"</li>
 * <li>{@code jsonb_typeof(payload) = 'object'}</li>
 * <li>{@code VARCHAR[]} 배열과 JSONB 의 매핑</li>
 * </ul>
 * 이 중 어느 것도 H2 에는 없다. H2 로 통과한 초록은 "PostgreSQL 에서 된다" 를 전혀 뜻하지
 * 않으면서 뜻하는 것처럼 보인다.
 *
 * <p>🔴 DB 를 못 구하면 <b>건너뜀으로 표시</b>된다 — 조용히 통과하지 않는다.
 * {@code PostgresAvailableCondition} 이 그 판정을 한다.
 *
 * <p>DB 를 잡는 장치({@code TestDatabase})는 추천 티켓(S15P21E201-543)이 만든 것을
 * <b>그대로 쓴다.</b> 복사하면 한쪽만 고쳐지는 날이 온다.
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
