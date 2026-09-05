package com.gabolle.backend.recommendation.config;

import java.util.concurrent.Executor;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * {@link RecommendationProperties}·{@link BaselineEngineProperties} 를 빈으로 만들고,
 * 비동기 Job 실행기를 연다.
 */
@Configuration
@EnableConfigurationProperties({ RecommendationProperties.class, BaselineEngineProperties.class })
@EnableAsync
public class RecommendationConfiguration {

	/**
	 * S15P21E201-192 비동기 Job 러너 전용 실행기 — {@code @Async("recommendationJobExecutor")}
	 * 로만 쓴다.
	 *
	 * <p>🔴 <b>이름 있는 빈을 따로 두는 이유.</b> Spring 기본 비동기 실행기
	 * ({@code SimpleAsyncTaskExecutor})는 스레드를 매번 새로 만들고 상한이 없다 — 추천
	 * 요청이 몰리면 스레드가 무한정 늘어나 다른 도메인(인증·이벤트)의 비동기 작업까지
	 * 굶길 수 있다. 이 실행기만 따로 두면 추천이 밀려도 그 영향이 이 안에서 끝난다.
	 */
	@Bean(name = "recommendationJobExecutor")
	public Executor recommendationJobExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(2);
		executor.setMaxPoolSize(8);
		executor.setQueueCapacity(50);
		executor.setThreadNamePrefix("rec-job-");
		executor.initialize();
		return executor;
	}
}
