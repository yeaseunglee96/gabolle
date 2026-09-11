package com.gabolle.backend.batch.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.gabolle.backend.batch.application.TasteVectorProperties;
import com.gabolle.backend.batch.security.InternalTokenAuthenticationFilter;

/**
 * 내부 배치 API 의 배선 — MLOps Phase 1.
 *
 * <p>🔴 필터를 {@code @Component} 로 두지 않는 이유. {@code OncePerRequestFilter} 를
 * 상속한 빈은 Spring Boot 가 <b>서블릿 필터로도 자동 등록</b>한다. 그러면 같은 필터가 두 번
 * 돈다 — 한 번은 Security 체인 밖에서, 한 번은 안에서. 밖에서 도는 쪽은 Security 의 순서
 * 보장을 안 받으므로, 무엇이 언제 도는지가 설정이 아니라 우연이 된다.
 * 그래서 여기서 빈으로만 만들고 {@code SecurityConfig} 가 체인 안에 직접 꽂는다.
 */
@Configuration
@EnableConfigurationProperties({ InternalApiProperties.class, TasteVectorProperties.class })
public class BatchConfiguration {

	@Bean
	InternalTokenAuthenticationFilter internalTokenAuthenticationFilter(InternalApiProperties properties) {
		return new InternalTokenAuthenticationFilter(properties);
	}
}
