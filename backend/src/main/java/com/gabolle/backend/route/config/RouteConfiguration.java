package com.gabolle.backend.route.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 경로 조회 설정을 스프링에 연결한다 — {@code OriginSearchConfiguration} 과 같은 방식이다.
 * 필드 기본값이 있어 설정 파일이 없어도 뜬다.
 */
@Configuration
@EnableConfigurationProperties(RouteProperties.class)
public class RouteConfiguration {
}
