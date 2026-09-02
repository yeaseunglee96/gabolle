package com.gabolle.backend.recommendation.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import com.gabolle.backend.recommendation.adapter.RecommendationEnginePort;

/**
 * 테스트에서만 {@link RecommendationEnginePort} 구현을 붙인다.
 *
 * <p>🔴 운영 코드에는 이 포트의 구현이 <b>없다</b>. 실제 추천 서버의 계약이 확정되지 않았고,
 * 지어낸 구현은 진짜 계약이 오면 버려지는데 그 사이에 그것을 믿는 코드가 붙기 때문이다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class RecommendationTestConfiguration {

	@Bean
	public FakeRecommendationEngine fakeRecommendationEngine() {
		return new FakeRecommendationEngine();
	}
}
