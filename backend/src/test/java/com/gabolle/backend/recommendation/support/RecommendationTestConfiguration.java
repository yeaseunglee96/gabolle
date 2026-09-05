package com.gabolle.backend.recommendation.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gabolle.backend.recommendation.adapter.RecommendationEnginePort;
import com.gabolle.backend.recommendation.application.port.ItineraryDraftPort;

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

	/**
	 * 🔴 S15P21E201-604 — {@link ItineraryDraftPort} 도 같은 이유로 여기서만 붙인다.
	 * {@code RecommendationSliceApplication} 이 {@code itinerary} 패키지를 스캔하지
	 * 않아 진짜 구현({@code ItineraryDraftService})이 이 슬라이스에는 없다. 이 대역이
	 * 없으면 {@code ITINERARY_GENERATION} 성공 경로 테스트가 전부
	 * {@code ERROR_ITINERARY_PORT_NOT_CONFIGURED} 로 실패한다.
	 */
	@Bean
	public FakeItineraryDraftPort fakeItineraryDraftPort(JdbcTemplate jdbcTemplate) {
		return new FakeItineraryDraftPort(jdbcTemplate);
	}
}
