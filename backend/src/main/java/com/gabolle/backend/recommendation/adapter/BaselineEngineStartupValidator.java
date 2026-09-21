package com.gabolle.backend.recommendation.adapter;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * 기동 시점에 추천 엔진이 배선됐는지 확인한다. 엔진 빈이 없어도 애플리케이션은 정상적으로
 * 뜨고, 그 뒤 모든 추천 요청이 ENGINE_NOT_CONFIGURED 로 조용히 실패한다.
 */
@Component
@Profile({ "db", "dev" })
// @ConditionalOnBean 을 달지 않는다. 자동 설정용 조건이라 직접 스캔하는 @Component 에서는
// 평가 시점이 스캔 순서에 달려 있고, 그러면 감시 대상인 엔진과 이 검사기가 함께 빠진다.
// 배선은 조건이 아니라 슬라이스의 스캔 목록으로 정한다.
public class BaselineEngineStartupValidator {

	private final ObjectProvider<RecommendationEnginePort> enginePort;

	public BaselineEngineStartupValidator(ObjectProvider<RecommendationEnginePort> enginePort) {
		this.enginePort = enginePort;
	}

	@PostConstruct
	void validate() {
		if (this.enginePort.getIfAvailable() == null) {
			throw new IllegalStateException(
					"장소 데이터 계층은 올라왔는데 RecommendationEnginePort 빈이 없다 — "
							+ "이대로 뜨면 모든 추천 요청이 ENGINE_NOT_CONFIGURED 로 조용히 실패한다. "
							+ "BaselineRecommendationEngine 의 @ConditionalOnBean 배선을 확인해라.");
		}
	}
}
