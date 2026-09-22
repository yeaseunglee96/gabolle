package com.gabolle.backend.notification.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 푸시 발송 설정을 스프링에 연결한다 — {@code ExchangeRateConfiguration} 과 같은 방식이다.
 * 칸마다 기본값이 있어 설정 파일이 없어도 뜬다 (S15P21E201-1391).
 */
@Configuration
@EnableConfigurationProperties(PushProperties.class)
public class NotificationConfiguration {
}
