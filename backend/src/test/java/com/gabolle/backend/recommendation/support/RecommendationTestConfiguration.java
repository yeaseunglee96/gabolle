package com.gabolle.backend.recommendation.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gabolle.backend.recommendation.adapter.RecommendationEnginePort;
import com.gabolle.backend.recommendation.application.port.ItineraryDraftPort;

/**
 * 테스트에서만 {@link RecommendationEnginePort} 구현을 붙인다.
 *
 * <p>전에는 운영 코드에 이 포트의 구현이 없었다 — 실제 추천 서버의 계약이 확정되지 않아
 * 지어내지 않았다. S15P21E201-548 이 규칙 기반 엔진({@code BaselineRecommendationEngine})을
 * 넣으면서 그 상태는 끝났고, 이 대역은 그 엔진을 대신하는 것이 아니라 <b>후보 목록을 정해
 * 두고 그 뒤를 재기 위한 것</b>으로 남는다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class RecommendationTestConfiguration {

	/**
	 * S15P21E201-808 — {@code @Primary} 가 붙은 이유.
	 *
	 * <p>이 티켓 전에는 이 대역이 슬라이스에 있는 유일한 엔진이었다. 이제
	 * {@code RecommendationSliceApplication} 이 {@code place} 를 올려서
	 * {@code BaselineRecommendationEngine} 도 함께 서고, 그러면 포트 구현이 둘이 되어
	 * {@code ObjectProvider.getIfAvailable()} 이 실패한다.
	 *
	 * <p>대역을 우선한다. 여기에 걸린 검사들은 후보 목록을 정해 두고 그 뒤의 조립·기록·
	 * 제약 처리를 재는 것이라, 진짜 엔진이 끼어들면 재는 대상이 바뀐다. 진짜 엔진은
	 * {@code RecommendationWithRealPlacesFunctionalTest} 가 실제 장소로 따로 잰다.
	 */
	@Bean
	@Primary
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
