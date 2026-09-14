package com.gabolle.testslice;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 개인정보 자동 정리 배치 통합 테스트가 띄우는 애플리케이션 — <b>공통·인증·사용자·개인정보정리만</b> 올린다.
 *
 * <p>{@code AuthSliceApplication} 과 같은 이유로 {@code com.gabolle.backend} 밖에 둔다.
 *
 * <p>{@code event.domain} 을 {@code @EntityScan} 에 더한 이유 — {@code PrivacyCleanupService} 가
 * {@code AccountDeletionService} 와 같은 방식으로 {@code EventOutbox} 를 JPQL 로 직접 지운다
 * ({@code event} 패키지 밖에서 {@code EventOutboxRepository} 를 부르지 않는다는 그 패키지의
 * 규칙을 지키면서, 엔티티 매핑만 빌린다). {@code event.repository} 는 올리지 않는다 — 이 슬라이스가
 * {@code EventOutboxRepository} 빈을 실제로 쓰지 않는다.
 *
 * <h2>🔴 {@code @EnableScheduling} — 2026-09-08 사고에서 빠졌던 것</h2>
 *
 * 처음 이 슬라이스를 만들 때 이 애노테이션을 빠뜨렸다. 그 결과 {@code PrivacyCleanupScheduler}의
 * {@code @Scheduled}가 {@code ScheduledAnnotationBeanPostProcessor}를 한 번도 안 거쳤고,
 * {@code cron} 속성의 {@code ${gabolle.privacy.cleanup.cron}} 플레이스홀더가 실제 운영
 * 프로필(`GabolleBackendApplication`, `@EnableScheduling` 있음)에서만 해석을 시도하다가
 * {@code application.properties}에 그 키가 없어서 배포가 통째로 죽었다 — 테스트는 전부 초록이었다.
 * 이 애노테이션이 있어야 이 슬라이스도 운영과 같은 경로(플레이스홀더 해석 포함)를 타서
 * 같은 종류의 버그를 다시 놓치지 않는다.
 */
// 🔴 S15P21E201-317 — trip 을 더했다. LocalAuthService(auth)가 가입 시 익명 여행 승계를 위해
//    AnonymousTripClaimService(trip.application)·JpaTripRepository(trip.infra) 를 물게
//    됐다 — AuthSliceApplication 과 같은 이유다.
@SpringBootApplication(scanBasePackages = {
		"com.gabolle.backend.common",
		"com.gabolle.backend.auth",
		"com.gabolle.backend.user",
		"com.gabolle.backend.privacy",
		"com.gabolle.backend.trip",
		// 🔴 S15P21E201-137 — place 를 더했다. 위의 trip 을 올리는 순간 그 패키지의 컨트롤러가
		//    전부 함께 올라오고, 그중 TripFacetViewController(S15P21E201-475)가 place 쪽
		//    서비스를 필수로 요구한다. 없으면 이 슬라이스가 통째로 못 뜬다.
		//    AuthSliceApplication 이 같은 이유로 같은 줄을 갖고 있다.
		"com.gabolle.backend.place",
		// S15P21E201-440 — AuthSliceApplication 과 같은 이유로 event 도 더한다. trip 의
		// SpendProfileService(-709)가 event 쪽 EventIngestService 를 필수로 요구한다.
		"com.gabolle.backend.event"
})
@EnableScheduling
@EntityScan(basePackages = {
		"com.gabolle.backend.auth.domain",
		"com.gabolle.backend.user.domain",
		"com.gabolle.backend.event.domain",
		"com.gabolle.backend.privacy.domain",
		"com.gabolle.backend.trip.infra",
		"com.gabolle.backend.place.domain"
})
@EnableJpaRepositories(basePackages = {
		"com.gabolle.backend.auth.repository",
		"com.gabolle.backend.user.repository",
		"com.gabolle.backend.privacy.repository",
		"com.gabolle.backend.trip.infra",
		"com.gabolle.backend.place.repository",
		"com.gabolle.backend.event.repository"
})
public class PrivacySliceApplication {
}
