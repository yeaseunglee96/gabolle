package com.gabolle.testslice;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 배치 통합 테스트가 띄우는 애플리케이션 — 배치·취향·공통만 올린다.
 *
 * <p>{@code FeedSliceApplication} 과 같은 이유이고 같은 모양이다. 전체를 띄우면 배치와
 * 아무 상관 없는 이유로 여기가 빨개지고, 그러면 빨간불이 무엇을 뜻하는지 아무도 모른다.
 *
 * <p>좁힌 것은 스캔 범위뿐이다 — Flyway 는 모든 마이그레이션을 그대로 적용한다.
 * 이 티켓이 실제로 검증해야 하는 것(조건부 UNIQUE 색인 {@code uq_user_taste_vector_current},
 * {@code ck_user_taste_weight_interaction_has_support}, JSONB, {@code TIMESTAMPTZ} 비교)은
 * 하나도 안 줄었다.
 */
@SpringBootApplication(
		scanBasePackages = { "com.gabolle.backend.common", "com.gabolle.backend.batch",
				"com.gabolle.backend.preference" })
@EntityScan(basePackages = { "com.gabolle.backend.preference.domain" })
@EnableJpaRepositories(basePackages = { "com.gabolle.backend.preference.repository" })
public class BatchSliceApplication {
}
