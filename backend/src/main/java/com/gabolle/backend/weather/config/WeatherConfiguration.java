package com.gabolle.backend.weather.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 기상청 단기예보 설정을 스프링에 연결한다. 필드 기본값이 있어 설정 파일이 없어도 뜬다.
 */
@Configuration
@EnableConfigurationProperties(WeatherProperties.class)
public class WeatherConfiguration {
}
