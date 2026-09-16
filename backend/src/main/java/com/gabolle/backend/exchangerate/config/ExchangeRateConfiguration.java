package com.gabolle.backend.exchangerate.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 환율 조회 설정을 스프링에 연결한다 — {@code WeatherConfiguration}과 같은 방식이다. 필드
 * 기본값이 있어 설정 파일이 없어도 뜬다.
 */
@Configuration
@EnableConfigurationProperties(ExchangeRateProperties.class)
public class ExchangeRateConfiguration {
}
