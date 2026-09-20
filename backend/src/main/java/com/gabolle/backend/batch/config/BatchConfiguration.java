package com.gabolle.backend.batch.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.gabolle.backend.batch.application.TasteVectorProperties;
import com.gabolle.backend.batch.security.InternalTokenAuthenticationFilter;

/**
 * 내부 배치 API 의 배선.
 *
 * <p>필터를 {@code @Component} 로 두지 않는다. {@code OncePerRequestFilter} 를 상속한 빈은
 * Spring Boot 가 서블릿 필터로도 자동 등록해서 같은 필터가 Security 체인 안팎에서 두 번 돌고,
 * 밖에서 도는 쪽은 순서 보장을 안 받는다. 여기서 빈으로만 만들고 {@code SecurityConfig} 가
 * 체인 안에 직접 꽂는다.
 */
@Configuration
@EnableConfigurationProperties({ InternalApiProperties.class, TasteVectorProperties.class })
public class BatchConfiguration {

	@Bean
	InternalTokenAuthenticationFilter internalTokenAuthenticationFilter(InternalApiProperties properties) {
		return new InternalTokenAuthenticationFilter(properties);
	}
}
