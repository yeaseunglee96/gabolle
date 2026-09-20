package com.gabolle.backend.tools.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 필드 기본값이 있어 설정 파일이 없어도 뜬다. {@code Clock} 빈은 {@code common.config.TimeConfiguration} 이 준다. */
@Configuration
@EnableConfigurationProperties(TranslateProperties.class)
public class TranslateConfiguration {
}
