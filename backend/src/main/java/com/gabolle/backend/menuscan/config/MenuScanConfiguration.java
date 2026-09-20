package com.gabolle.backend.menuscan.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 메뉴판 읽기 설정을 스프링에 연결한다.
 *
 * <p>이 파일이 없으면 서버가 안 뜬다. {@code @ConfigurationProperties} 는 스스로 빈이 되지 않고,
 * 컴파일은 통과하므로 기동해 봐야 안다.
 *
 * <p>필드 기본값이 있어 설정 파일이 없어도 뜬다. 키가 비면 호출이 명확한 실패로 끝날 뿐 기동은 막지
 * 않는다.
 */
@Configuration
@EnableConfigurationProperties(MenuScanProperties.class)
public class MenuScanConfiguration {
}
