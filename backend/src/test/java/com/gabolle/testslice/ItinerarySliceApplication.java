package com.gabolle.testslice;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * JPA 저장소 통합 테스트가 띄우는 애플리케이션 — 공통·일정만 올린다.
 *
 * <p>{@link TripSliceApplication} 과 같은 이유로 패키지를 따로 둔다 — {@code com.gabolle.backend}
 * 안에 두면 본 애플리케이션의 컴포넌트 스캔에 걸려 {@code no-db} 프로필에서도
 * {@code @EnableJpaRepositories} 가 켜진다.
 */
/*
 * trip · place 를 스캔 범위에 더했다.
 *    itinerary/application/ItineraryDraftService 가 여행 기간·출발지(TripRepository)와
 *    장소 좌표(PlaceRepository)를 읽어야 일정 초안을 만들 수 있고, itinerary/application/
 *    ItineraryQueryService 가 "이 판을 만든 추천이 무엇이었나"(RecommendationJobRepository)를
 *    읽어야 fallbackMode 를 채울 수 있기 때문이다. 그 빈에
 *    조건부 배선(@ConditionalOnBean)을 붙여 슬라이스에서만 빠지게 하는 방법도 있었지만
 *    그러면 운영에서도 조용히 빠질 수 있는 자리가 하나 늘어난다 — 운영 배선은 무조건
 *    붙거나 기동이 실패하는 편이 낫다. 그래서 조건이 아니라 테스트 범위를 넓혔다.
 */
@SpringBootApplication(scanBasePackages = {
		"com.gabolle.backend.common",
		"com.gabolle.backend.itinerary",
		"com.gabolle.backend.trip",
		"com.gabolle.backend.user",
		"com.gabolle.backend.place",
		"com.gabolle.backend.recommendation",
		"com.gabolle.backend.event"
})
@EntityScan(basePackages = {
		"com.gabolle.backend.itinerary.infra",
		"com.gabolle.backend.trip.infra",
		"com.gabolle.backend.user.domain",
		"com.gabolle.backend.place.domain",
		"com.gabolle.backend.recommendation.domain",
		"com.gabolle.backend.event.domain"
})
@EnableJpaRepositories(basePackages = {
		"com.gabolle.backend.itinerary.infra",
		"com.gabolle.backend.trip.infra",
		"com.gabolle.backend.user.repository",
		"com.gabolle.backend.place.repository",
		"com.gabolle.backend.recommendation.repository",
		"com.gabolle.backend.event.repository"
})
public class ItinerarySliceApplication {
}
