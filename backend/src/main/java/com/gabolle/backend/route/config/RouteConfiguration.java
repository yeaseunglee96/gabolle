package com.gabolle.backend.route.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import com.gabolle.backend.route.transit.TransitProperties;

/**
 * 경로 조회 설정을 스프링에 연결한다. @ConfigurationProperties 만 붙인 클래스는 스스로 빈이
 * 되지 않아, 여기 등록하지 않으면 서버는 뜨는데 설정이 조용히 안 읽힌다.
 */
@Configuration
@EnableConfigurationProperties({ RouteProperties.class, TransitProperties.class })
public class RouteConfiguration {
}
