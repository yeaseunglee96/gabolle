package com.gabolle.testslice;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 피드 통합 테스트가 띄우는 애플리케이션 — <b>피드·취향·공통만</b> 올린다.
 *
 * <p>{@code RecommendationSliceApplication}(S15P21E201-192)과 같은 이유이고 같은 모양이다.
 * 애플리케이션 전체를 띄우면 <b>다른 도메인이 반쯤 만들어 둔 빈까지 전부 살아나야</b>
 * 이 테스트가 돈다. 그러면 피드와 아무 상관 없는 이유로 여기가 빨개지고, 빨간불이
 * 무엇을 뜻하는지 아무도 모르게 된다.
 *
 * <p>🔴 그래도 <b>진짜 PostgreSQL 위에서 돈다</b> — Flyway 가 모든 마이그레이션을 그대로
 * 적용한다. 좁힌 것은 스캔 범위뿐이고, 이 티켓이 실제로 검증해야 하는 것
 * (JSONB · UUID · {@code VARCHAR[]} · 조건부 UNIQUE 색인 · CHECK)은 하나도 안 줄었다.
 *
 * <p>🔴 {@code com.gabolle.backend} 밖에 있는 이유도 같다 — 안에 두면 본 애플리케이션의
 * 컴포넌트 스캔에 걸려 {@code no-db} 프로필에서도 JPA 저장소가 켜지고, EntityManagerFactory
 * 가 없어 {@code contextLoads} 가 죽는다. 이미 한 번 그렇게 깨진 적이 있다.
 */
@SpringBootApplication(scanBasePackages = { "com.gabolle.backend.common", "com.gabolle.backend.feed",
		"com.gabolle.backend.preference" })
@EntityScan(basePackages = { "com.gabolle.backend.feed.domain", "com.gabolle.backend.preference.domain" })
@EnableJpaRepositories(basePackages = { "com.gabolle.backend.feed.repository",
		"com.gabolle.backend.preference.repository" })
public class FeedSliceApplication {
}
