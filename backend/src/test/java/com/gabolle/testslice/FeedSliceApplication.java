package com.gabolle.testslice;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 피드 통합 테스트가 띄우는 애플리케이션 — 피드·취향·공통만 올린다.
 *
 * <p>전체를 띄우면 다른 도메인이 반쯤 만들어 둔 빈까지 살아나야 이 테스트가 돌고, 피드와 상관없는
 * 이유로 빨개진다. 좁힌 것은 스캔 범위뿐이다 — Flyway 가 모든 마이그레이션을 적용하므로
 * JSONB·UUID·{@code VARCHAR[]}·조건부 UNIQUE 색인·CHECK 검증은 안 줄었다.
 *
 * <p>{@code com.gabolle.backend} 밖에 둔다. 안에 두면 본 애플리케이션 스캔에 걸려 {@code no-db}
 * 프로필에서도 JPA 저장소가 켜지고 {@code contextLoads} 가 죽는다.
 */
@SpringBootApplication(scanBasePackages = { "com.gabolle.backend.common", "com.gabolle.backend.feed",
		"com.gabolle.backend.preference" })
@EntityScan(basePackages = { "com.gabolle.backend.feed.domain", "com.gabolle.backend.preference.domain" })
@EnableJpaRepositories(basePackages = { "com.gabolle.backend.feed.repository",
		"com.gabolle.backend.preference.repository" })
public class FeedSliceApplication {
}
