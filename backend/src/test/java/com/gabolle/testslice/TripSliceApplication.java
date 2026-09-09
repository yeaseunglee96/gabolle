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
		"com.gabolle.backend.user"
})
@EntityScan(basePackages = { "com.gabolle.backend.trip.infra", "com.gabolle.backend.user.domain" })
@EnableJpaRepositories(basePackages = { "com.gabolle.backend.trip.infra", "com.gabolle.backend.user.repository" })
public class TripSliceApplication {
}
