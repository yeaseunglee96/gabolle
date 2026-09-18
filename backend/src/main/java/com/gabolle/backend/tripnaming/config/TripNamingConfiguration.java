package com.gabolle.backend.tripnaming.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 여행 이름 짓기 설정을 스프링에 연결한다 — S15P21E201-1025.
 *
 * <p>🔴 <b>이 파일이 없으면 서버가 안 뜬다.</b> {@code @ConfigurationProperties} 는 스스로
 * 빈이 되지 않는다 — 컴파일은 멀쩡히 통과하고 기동해 봐야 안다. 메뉴판 읽기에서 실제로
 * 이 파일을 빠뜨렸다가 시험을 쓰면서 잡았다.
 */
@Configuration
@EnableConfigurationProperties(TripNamingProperties.class)
public class TripNamingConfiguration {
}
