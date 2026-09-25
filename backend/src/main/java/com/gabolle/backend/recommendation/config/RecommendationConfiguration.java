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
	 *
	 * <p>동시 4개 (S15P21E201-1687, 2026-09-25 사용자 결정 — 전에는 2). 줄(50)이 다 차야 최대(8)까지 늘어나므로, 평소
	 * 동시에 도는 수는 이 기본 수다. 운영과 같은 한도(앱 CPU 2)로 이 PC 로컬에서 재 보니, 무거운 추천 30명이 한꺼번에
	 * 누르면 가장 느린 사람의 기다림이 78~92초 → 54초로 줄고, 그 동안 가벼운 요청(피드·장소 상세·여행 목록)의 보통 응답은
	 * 20ms 그대로였다. CPU 가 이미 2개 꽉 차 있어 한 건 처리는 조금 느려지지만(무거운 것 4.2 → 6초) 줄이 빨리 빠진다.
	 */
	@Bean(name = "recommendationJobExecutor")
	public Executor recommendationJobExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(4);
		executor.setMaxPoolSize(8);
		executor.setQueueCapacity(50);
		executor.setThreadNamePrefix("rec-job-");
		executor.initialize();
		return executor;
	}
}
