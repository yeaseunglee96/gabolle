package com.gabolle.backend.recommendation.support;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * 카프카를 못 구하면 그 테스트 클래스를 건너뛴 것으로 표시한다.
 *
 * <p>{@link PostgresAvailableCondition} 과 같은 이유로 {@code @EnabledIf} 를 쓰지 않는다 —
 * 부모 클래스에 달면 자식 테스트 클래스에서 조건이 돌지 않아, 건너뜀이 아니라 실패로 떨어진다.
 */
public class KafkaAvailableCondition implements ExecutionCondition {

	@Override
	public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
		if (TestKafka.isAvailable()) {
			return ConditionEvaluationResult.enabled("카프카를 쓸 수 있다");
		}
		return ConditionEvaluationResult
			.disabled("카프카가 없어 건너뛴다 — GABOLLE_TEST_KAFKA_BOOTSTRAP 을 주거나 도커를 켜십시오");
	}
}
