package com.gabolle.backend.auth.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * {@link AnonymousQuotaInterceptor} 를 비회원이 무언가를 «만드는» 세 경로에만 건다. 메서드는 인터셉터가
 * 다시 본다(POST 만) — 같은 경로의 GET 목록 조회까지 막으면 안 된다.
 */
@Configuration
@Profile({"db", "dev"})
public class AnonymousQuotaConfig implements WebMvcConfigurer {

	private final AnonymousQuotaInterceptor interceptor;

	public AnonymousQuotaConfig(AnonymousQuotaInterceptor interceptor) {
		this.interceptor = interceptor;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(interceptor)
				.addPathPatterns("/api/v1/trips", "/api/v1/shares/*/clone", "/api/v1/trips/*/recommendation-jobs");
	}
}
