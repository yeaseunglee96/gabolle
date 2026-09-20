package com.gabolle.testslice;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * (동행자 초대·참여자·공유 주소·복제) 통합 테스트가 띄우는 애플리케이션.
 *
 * <p>{@link ItinerarySliceApplication} 에 공유({@code share})와 사용자({@code user})를 더한 것이다 —
 * 참여자 목록·최근 변경이 표시 이름을 {@code app_user} 에서 읽고, 복제가 추천 Job 을 접수하기
 * 때문이다. 다른 슬라이스와 같은 이유로 {@code com.gabolle.testslice} 패키지에 둔다 — 본 애플리케이션의
 * 컴포넌트 스캔에 걸리지 않게.
 */
@SpringBootApplication(scanBasePackages = {
		"com.gabolle.backend.common",
		"com.gabolle.backend.itinerary",
		"com.gabolle.backend.trip",
		"com.gabolle.backend.share",
		"com.gabolle.backend.user",
		"com.gabolle.backend.place",
		"com.gabolle.backend.recommendation",
		"com.gabolle.backend.event"
})
@EntityScan(basePackages = {
		"com.gabolle.backend.itinerary.infra",
		"com.gabolle.backend.trip.infra",
		"com.gabolle.backend.share.domain",
		"com.gabolle.backend.user.domain",
		"com.gabolle.backend.place.domain",
		"com.gabolle.backend.recommendation.domain",
		"com.gabolle.backend.event.domain"
})
@EnableJpaRepositories(basePackages = {
		"com.gabolle.backend.itinerary.infra",
		"com.gabolle.backend.trip.infra",
		"com.gabolle.backend.share.repository",
		"com.gabolle.backend.user.repository",
		"com.gabolle.backend.place.repository",
		"com.gabolle.backend.recommendation.repository",
		"com.gabolle.backend.event.repository"
})
public class CollaborationSliceApplication {
}
