package com.gabolle.backend.transit.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * TAGO 버스 정류소·도착정보 설정을 스프링에 연결한다 — {@code WeatherConfiguration}과 같은
 * 방식이다. 필드 기본값이 있어 설정 파일이 없어도 뜬다.
 */
@Configuration
@EnableConfigurationProperties(TransitProperties.class)
public class TransitConfiguration {
}
