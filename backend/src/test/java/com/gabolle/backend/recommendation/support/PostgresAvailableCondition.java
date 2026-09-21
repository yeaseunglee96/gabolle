package com.gabolle.backend.recommendation.support;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * PostgreSQL 을 못 구하면 그 테스트 클래스를 건너뛴 것으로 표시한다.
 *
 * {@code @EnabledIf} 를 쓰지 않는다 — 부모 클래스에 달면 자식 테스트 클래스에서 조건이 돌지
 * 않아, 도커도 DB 도 없는 PC 에서 건너뜀이 아니라 실패로 떨어진다. {@code @ExtendWith} 로
 * 등록한 확장은 부모에서 자식으로 확실히 상속된다.
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
