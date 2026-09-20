package com.gabolle.backend.place.adapter;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 출발지 검색 설정을 등록한다. {@code OriginSearchProperties} 는 여기서만
 * {@code @EnableConfigurationProperties} 로 연결한다 — 필드 기본값이 있어 설정 파일이 없어도 뜬다.
 */
@Configuration
@Profile({"db", "dev"})
@EnableConfigurationProperties(OriginSearchProperties.class)
public class OriginSearchConfiguration {
}
