package com.gabolle.backend.common.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 시각을 주입 가능한 것으로 만든다.
 *
 * <p>{@code OffsetDateTime.now()} 를 코드 안에서 직접 부르면 "이 이벤트의 occurred_at 이
 * 맞나" 를 테스트에서 확인할 방법이 없다. {@link Clock} 을 빈으로 두면 테스트가 고정된
 * 시각을 넣을 수 있다.
 *
 * <p>서버와 DB의 시간대가 달라도 저장 시각이 흔들리지 않도록 UTC를 기준으로 사용한다.
 */
@Configuration
public class TimeConfiguration {

	@Bean
	public Clock clock() {
		return Clock.systemUTC();
	}
}
