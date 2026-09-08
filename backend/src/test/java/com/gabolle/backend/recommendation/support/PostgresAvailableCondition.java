package com.gabolle.backend.recommendation.support;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * PostgreSQL 을 못 구하면 그 테스트 클래스를 <b>건너뛴 것으로</b> 표시한다.
 *
 * <p>🔴 {@code @EnabledIf} 를 쓰지 않는 이유가 있다. 그 애너테이션을 <b>부모 클래스</b>에
 * 달았더니 자식 테스트 클래스에서 조건이 돌지 않았고, 그 결과 도커도 DB 도 없는 PC 에서
 * 테스트가 <b>건너뜀이 아니라 실패</b>로 떨어졌다. {@code @ExtendWith} 로 등록한 확장은
 * 부모 클래스에서 자식으로 확실히 상속된다 — JUnit 이 보장하는 자리다.
 *
 * <p>건너뛴 것과 통과한 것을 구분하는 일이 이 티켓에서 특히 중요하다. 여기서 도는 테스트가
 * JSONB · UUID · 배열 · DB 제약처럼 <b>PostgreSQL 에서만 진짜로 검증되는 것</b>이라, 조용히
 * 통과하면 아무도 검증되지 않았다는 사실을 모른다.
 */
public class PostgresAvailableCondition implements ExecutionCondition {

	@Override
	public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
		if (TestDatabase.isAvailable()) {
			return ConditionEvaluationResult.enabled("PostgreSQL 을 쓸 수 있다");
		}
		return ConditionEvaluationResult.disabled(
				"PostgreSQL 이 없어 건너뛴다 — GABOLLE_TEST_DB_URL 을 주거나 도커를 켜십시오");
	}
}
