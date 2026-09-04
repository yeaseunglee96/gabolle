package com.gabolle.testslice;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * S15P21E201-313 JPA 저장소 통합 테스트가 띄우는 애플리케이션 — <b>공통·일정만</b> 올린다.
 *
 * <p>{@link TripSliceApplication} 과 같은 이유로 패키지를 따로 둔다 — {@code com.gabolle.backend}
 * 안에 두면 본 애플리케이션의 컴포넌트 스캔에 걸려 {@code no-db} 프로필에서도
 * {@code @EnableJpaRepositories} 가 켜진다.
 */
@SpringBootApplication(scanBasePackages = {
		"com.gabolle.backend.common",
		"com.gabolle.backend.itinerary"
})
@EntityScan(basePackages = "com.gabolle.backend.itinerary.infra")
@EnableJpaRepositories(basePackages = "com.gabolle.backend.itinerary.infra")
public class ItinerarySliceApplication {
}
