package com.gabolle.testslice;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

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
 */
@SpringBootApplication(scanBasePackages = {
		"com.gabolle.backend.common",
		"com.gabolle.backend.auth",
		"com.gabolle.backend.user",
		"com.gabolle.backend.privacy"
})
@EntityScan(basePackages = {
		"com.gabolle.backend.auth.domain",
		"com.gabolle.backend.user.domain",
		"com.gabolle.backend.event.domain",
		"com.gabolle.backend.privacy.domain"
})
@EnableJpaRepositories(basePackages = {
		"com.gabolle.backend.auth.repository",
		"com.gabolle.backend.user.repository",
		"com.gabolle.backend.privacy.repository"
})
public class PrivacySliceApplication {
}
