package com.gabolle.backend.recommendation.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gabolle.backend.recommendation.adapter.RecommendationEnginePort;
import com.gabolle.backend.recommendation.application.port.ItineraryDraftPort;

/**
 * 테스트에서만 {@link RecommendationEnginePort} 구현을 붙인다. 이 대역은 진짜 엔진을
 * 대신하는 것이 아니라 후보 목록을 정해 두고 그 뒤를 재기 위한 것이다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class RecommendationTestConfiguration {

	/**
	 * {@code RecommendationSliceApplication} 이 {@code place} 를 올려
	 * {@code BaselineRecommendationEngine} 도 함께 서므로, {@code @Primary} 가 없으면 포트
	 * 구현이 둘이 되어 {@code ObjectProvider.getIfAvailable()} 이 실패한다. 이 슬라이스의
	 * 검사는 후보 목록을 정해 두고 그 뒤의 조립·기록·제약 처리를 재므로 대역을 우선한다 —
	 * 진짜 엔진은 {@code RecommendationWithRealPlacesFunctionalTest} 가 따로 잰다.
	 */
	@Bean
	@Primary
	public FakeRecommendationEngine fakeRecommendationEngine() {
		return new FakeRecommendationEngine();
	}

	/**
	 * {@link ItineraryDraftPort} 도 여기서만 붙인다. 이 슬라이스는 {@code itinerary} 를
	 * 스캔하지 않아 진짜 구현이 없고, 대역이 없으면 {@code ITINERARY_GENERATION} 성공 경로가
	 * 전부 {@code ERROR_ITINERARY_PORT_NOT_CONFIGURED} 로 실패한다.
	 */
	@Bean
	public FakeItineraryDraftPort fakeItineraryDraftPort(JdbcTemplate jdbcTemplate) {
		return new FakeItineraryDraftPort(jdbcTemplate);
	}
}
