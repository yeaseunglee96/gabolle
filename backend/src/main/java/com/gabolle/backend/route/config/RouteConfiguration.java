package com.gabolle.backend.route.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import com.gabolle.backend.route.transit.TransitProperties;

/**
 * 경로 조회 설정을 스프링에 연결한다 — {@code OriginSearchConfiguration} 과 같은 방식이다.
 * 필드 기본값이 있어 설정 파일이 없어도 뜬다.
 *
 * <p>🔴 {@code @ConfigurationProperties} 만 붙인 클래스는 <b>스스로 빈이 되지 않는다.</b>
 * 여기 등록하지 않으면 컴파일도 되고 서버도 뜨는데 설정이 조용히 안 읽힌다 — 이 저장소가
 * 이미 겪은 고장이라 {@code TransitProperties} 도 같은 자리에 등록한다 (S15P21E201-1104).
 */
@Configuration
@EnableConfigurationProperties({ RouteProperties.class, TransitProperties.class })
public class RouteConfiguration {
}
