package com.gabolle.backend.tripnaming.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 여행 이름 짓기 설정을 스프링에 연결한다. {@code @ConfigurationProperties} 는 스스로 빈이
 * 되지 않으므로 이 등록이 빠지면 컴파일은 통과하고 기동에서 실패한다.
 */
@Configuration
@EnableConfigurationProperties(TripNamingProperties.class)
public class TripNamingConfiguration {
}
