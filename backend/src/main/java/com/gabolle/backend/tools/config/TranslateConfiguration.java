package com.gabolle.backend.tools.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 번역 중계 설정을 스프링에 연결한다 — {@code RouteConfiguration} 과 같은 방식이다.
 * 필드 기본값이 있어 설정 파일이 없어도 뜬다. {@code Clock} 빈은
 * {@code common.config.TimeConfiguration} 이 이미 전역으로 제공한다.
 */
@Configuration
@EnableConfigurationProperties(TranslateProperties.class)
public class TranslateConfiguration {
}
