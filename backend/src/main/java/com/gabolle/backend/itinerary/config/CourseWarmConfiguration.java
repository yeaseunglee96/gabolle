package com.gabolle.backend.itinerary.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 코스 2·3안 미리 짜기 전용 실행기 (S15P21E201-1604) — {@code @Async("courseWarmExecutor")} 로만 쓴다.
 *
 * <p>추천 작업 실행기(동시 4개 — S15P21E201-1687 전에는 2개)에서 하면 미리 짜는 약 3초 동안 다음 추천이 밀린다. 작게 따로 둔다. 넘치면 버린다 —
 * 미리 짜기는 덤이라 못 해도 첫 부름에 짜면 된다. 버리지 않고 예외를 던지면 그 예외가 추천 작업 스레드로 샌다.
 */
@Configuration
@Profile({ "db", "dev" })
public class CourseWarmConfiguration {

	@Bean(name = "courseWarmExecutor")
	public Executor courseWarmExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(1);
		executor.setMaxPoolSize(2);
		executor.setQueueCapacity(20);
		executor.setThreadNamePrefix("course-warm-");
		executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
		executor.initialize();
		return executor;
	}
}
