package com.gabolle.testslice;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 개인정보 자동 정리 배치 통합 테스트가 띄우는 애플리케이션 — 공통·인증·사용자·개인정보정리만 올린다.
 *
 * <p>{@code AuthSliceApplication} 과 같은 이유로 {@code com.gabolle.backend} 밖에 둔다.
 *
 * <p>{@code event.domain} 은 {@code PrivacyCleanupService} 가 {@code EventOutbox} 를 JPQL 로 직접
 * 지우기 때문에 매핑만 빌린다. {@code event.repository} 는 안 올린다 — 그 빈을 쓰지 않는다.
 *
 * <p>{@code @EnableScheduling} 이 있어야 {@code @Scheduled} 의
 * {@code ${gabolle.privacy.cleanup.cron}} 플레이스홀더가 이 슬라이스에서도 해석된다. 없으면 그 경로를
 * 한 번도 안 타서, 키가 빠져 있어도 테스트는 전부 초록이고 배포만 죽는다.
 */
// trip: LocalAuthService 가 가입 시 익명 여행 승계를 위해 AnonymousTripClaimService·
//       JpaTripRepository 를 문다.
@SpringBootApplication(scanBasePackages = {
		"com.gabolle.backend.common",
		"com.gabolle.backend.auth",
		"com.gabolle.backend.user",
		"com.gabolle.backend.privacy",
		"com.gabolle.backend.trip",
		// trip 을 올리면 그 패키지의 컨트롤러가 함께 올라오고, TripFacetViewController 가 place
		// 서비스를 요구한다. 없으면 이 슬라이스가 통째로 못 뜬다.
		"com.gabolle.backend.place",
		// trip 의 SpendProfileService 가 event 의 EventIngestService 를 요구한다.
		"com.gabolle.backend.event",
		// AccountDeletionService 가 StorageCleanupService(story.application)를 요구한다.
		"com.gabolle.backend.story.application",
		"com.gabolle.backend.story.storage"
})
@EnableScheduling
@EntityScan(basePackages = {
		"com.gabolle.backend.auth.domain",
		"com.gabolle.backend.user.domain",
		"com.gabolle.backend.event.domain",
		"com.gabolle.backend.privacy.domain",
		"com.gabolle.backend.trip.infra",
		"com.gabolle.backend.place.domain",
		// RecommendationJobRepository 가 관리하는 엔티티의 매핑. 없으면 "관리 대상 아님" 으로
		// 빈 자체를 못 만든다.
		"com.gabolle.backend.recommendation.domain",
		// AccountDeletionService.deleteUploadedImages 가 UploadedImage 를 JPQL 로 지운다.
		"com.gabolle.backend.story.domain"
})
@EnableJpaRepositories(basePackages = {
		"com.gabolle.backend.auth.repository",
		"com.gabolle.backend.user.repository",
		"com.gabolle.backend.privacy.repository",
		"com.gabolle.backend.trip.infra",
		"com.gabolle.backend.place.repository",
		"com.gabolle.backend.event.repository",
		// AnalyticsQueryService(event.application)가 RecommendationJobRepository 를 요구한다.
		"com.gabolle.backend.recommendation.repository",
		// story.application 의 서비스들이 쓰는 저장소.
		"com.gabolle.backend.story.repository"
})
public class PrivacySliceApplication {
}
