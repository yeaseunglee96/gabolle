package com.gabolle.backend.assistant.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * AI 여행 도우미 설정을 스프링에 연결한다 — {@code TranslateConfiguration} 과 같은 방식이다.
 * 필드 기본값이 있어 설정 파일이 없어도 뜬다.
 */
@Configuration
@EnableConfigurationProperties(AssistantProperties.class)
public class AssistantConfiguration {
}
