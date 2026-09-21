package com.gabolle.backend.dish.config;

import java.util.concurrent.Executor;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 음식 설명·그림 설정을 스프링에 연결하고, 그림을 만드는 실행기를 연다.
 *
 * <p>{@code @ConfigurationProperties} 는 스스로 빈이 되지 않는다. 여기서 등록하지 않으면 컴파일은
 * 통과하고 기동이 죽는다.
 */
@Configuration
@EnableConfigurationProperties(DishProperties.class)
@EnableAsync
public class DishConfiguration {

	/**
	 * 그림 만들기 전용 실행기 — {@code @Async("dishImageExecutor")} 로만 쓴다.
	 *
	 * <p>그림 한 장이 10~46초 동안 스레드를 붙잡는다. 스프링 기본 비동기 실행기
	 * ({@code SimpleAsyncTaskExecutor})는 상한이 없어 동시에 여럿이 누르면 스레드가 그만큼 살아
	 * 있게 된다. 상한을 두면 그 영향이 이 안에서 끝난다.
	 *
	 * <p>줄이 꽉 차면 기본 동작({@code AbortPolicy})이 예외를 던지고 {@code DishService.handOff}
	 * 가 받아 행을 실패로 적는다. 조용히 버리면 행이 {@code PENDING} 인 채로 남아
	 * {@code UNIQUE(name_key)} 때문에 그 음식이 영원히 굳는다.
	 */
	@Bean(name = "dishImageExecutor")
	public Executor dishImageExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(2);
		executor.setMaxPoolSize(4);
		executor.setQueueCapacity(20);
		executor.setThreadNamePrefix("dish-image-");
		executor.initialize();
		return executor;
	}
}
