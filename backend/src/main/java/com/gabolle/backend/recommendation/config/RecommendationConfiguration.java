package com.gabolle.backend.recommendation.config;

import java.util.concurrent.Executor;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * {@link RecommendationProperties}·{@link BaselineEngineProperties}·
 * {@link PreferenceAlignmentWeights}·{@link DiversityProperties} 를 빈으로 만들고,
 * 비동기 Job 실행기를 연다.
 */
@Configuration
@EnableConfigurationProperties({ RecommendationProperties.class, BaselineEngineProperties.class,
		PreferenceAlignmentWeights.class, DiversityProperties.class })
@EnableAsync
public class RecommendationConfiguration {

	/**
	 * 추천 Job 전용 실행기 — {@code @Async("recommendationJobExecutor")} 로만 쓴다. Spring
	 * 기본 실행기({@code SimpleAsyncTaskExecutor})는 스레드를 매번 새로 만들고 상한이 없어,
	 * 추천 요청이 몰리면 다른 도메인의 비동기 작업까지 굶길 수 있다.
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
