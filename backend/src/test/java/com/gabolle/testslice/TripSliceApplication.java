package com.gabolle.testslice;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * S15P21E201-461 JPA 저장소 통합 테스트가 띄우는 애플리케이션 — <b>공통·여행만</b> 올린다.
 *
 * <p>{@link RecommendationSliceApplication} 과 같은 이유로 패키지를 따로 둔다 —
 * {@code com.gabolle.backend} 안에 두면 본 애플리케이션의 컴포넌트 스캔에 걸려
 * {@code no-db} 프로필에서도 {@code @EnableJpaRepositories} 가 켜진다.
 */
@SpringBootApplication(scanBasePackages = {
		"com.gabolle.backend.common",
		"com.gabolle.backend.trip",
		// 2026-09-07 — 참여자 목록(TripMemberService)이 표시 이름을 app_user 에서 읽는다.
		"com.gabolle.backend.user",
		// S15P21E201-709 — SpendProfileService가 EventIngestService를 물어서 필요해졌다.
		"com.gabolle.backend.event",
		// 2026-09-10 (S15P21E201-475) — 갈래 열람 기록 경로(TripFacetViewController)가
		// place 쪽 기록 서비스를 부른다. 조건부 배선(@ConditionalOnBean)으로 슬라이스에서만
		// 빠지게 하는 방법도 있었지만 그러면 운영에서도 조용히 빠질 수 있는 자리가 하나
		// 늘어난다 — 운영 배선은 무조건 붙거나 기동이 실패하는 편이 낫다.
		// ItinerarySliceApplication 이 같은 판단을 먼저 적어 뒀다.
		"com.gabolle.backend.place"
})
@EntityScan(basePackages = {
		"com.gabolle.backend.trip.infra", "com.gabolle.backend.user.domain", "com.gabolle.backend.event.domain",
		"com.gabolle.backend.place.domain"
})
@EnableJpaRepositories(basePackages = {
		"com.gabolle.backend.trip.infra", "com.gabolle.backend.user.repository",
		"com.gabolle.backend.event.repository", "com.gabolle.backend.place.repository"
})
public class TripSliceApplication {
}
