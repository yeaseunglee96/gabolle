package com.gabolle.backend.dish.config;

import java.util.concurrent.Executor;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 음식 설명·그림 설정을 스프링에 연결하고, 그림을 만드는 실행기를 연다 — S15P21E201-1272.
 *
 * <p>🔴 {@code @ConfigurationProperties} 는 스스로 빈이 되지 않는다. 여기서 등록하지
 * 않으면 <b>컴파일은 통과하고 기동이 죽는다</b> — {@code MenuScanConfiguration} 이
 * 같은 자리를 지킨다.
 */
@Configuration
@EnableConfigurationProperties(DishProperties.class)
@EnableAsync
public class DishConfiguration {

	/**
	 * 그림 만들기 전용 실행기 — {@code @Async("dishImageExecutor")} 로만 쓴다.
	 *
	 * <h2>🔴 이름 있는 실행기를 따로 두는 이유</h2>
	 *
	 * 그림 한 장이 <b>10~46초</b> 동안 스레드를 붙잡는다(2026-09-18 실측). 스프링 기본
	 * 비동기 실행기({@code SimpleAsyncTaskExecutor})는 스레드를 매번 새로 만들고 상한이
	 * 없어서, 식당에서 여럿이 동시에 누르면 <b>수십 개의 스레드가 각자 40초씩</b> 살아
	 * 있게 된다. 여기에 상한을 두면 그림이 밀려도 그 영향이 이 안에서 끝난다.
	 *
	 * <h2>줄이 꽉 차면 거절한다 — 조용히 버리지 않는다</h2>
	 *
	 * 기본 동작({@code AbortPolicy})이 예외를 던지고, 그것을 {@code DishService.handOff}
	 * 가 받아 행을 <b>실패로 적는다.</b> 조용히 버리거나 그냥 올려보내면 행이
	 * {@code PENDING} 인 채로 남는데, 표의 {@code UNIQUE(name_key)} 때문에 <b>그 음식은
	 * 영원히 굳는다</b> — 다음 사람이 눌러도 「만드는 중」만 보고 새로 만들 수도 없다.
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
