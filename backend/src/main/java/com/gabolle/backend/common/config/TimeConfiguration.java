package com.gabolle.backend.common.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 시각을 빈으로 두어 테스트가 고정된 시각을 넣을 수 있게 한다.
 * 서버와 DB 의 시간대가 달라도 저장 시각이 흔들리지 않도록 UTC 를 쓴다.
 */
@Configuration
public class TimeConfiguration {

	@Bean
	public Clock clock() {
		return Clock.systemUTC();
	}
}
