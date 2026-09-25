package com.gabolle.backend.recommendation.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 추천 실행기 크기는 사용자가 정한 값이다 (S15P21E201-1687) — 동시 4 · 최대 8 · 줄 50. 몰릴 때의 기다림과 다른 기능의
 * 응답을 재 보고 고른 값이라, 바꾸려면 다시 재고 정한다. 모르고 바뀌면 여기서 잡힌다.
 */
class RecommendationExecutorSizeTest {

	@Test
	@DisplayName("추천 실행기는 동시 4 · 최대 8 · 줄 50 — 2026-09-25 사용자 결정(전에는 동시 2)")
	void recommendationExecutorRunsFourAtOnce() {
		ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) new RecommendationConfiguration()
				.recommendationJobExecutor();
		try {
			assertThat(executor.getCorePoolSize()).isEqualTo(4);
			assertThat(executor.getMaxPoolSize()).isEqualTo(8);
			assertThat(executor.getQueueCapacity()).isEqualTo(50);
		}
		finally {
			executor.shutdown();
		}
	}
}
