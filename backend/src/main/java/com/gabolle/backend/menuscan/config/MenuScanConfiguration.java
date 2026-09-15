package com.gabolle.backend.menuscan.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 메뉴판 읽기 설정을 스프링에 연결한다 — S15P21E201-1025.
 *
 * <p>🔴 <b>이 파일이 없으면 서버가 아예 안 뜬다.</b> {@code @ConfigurationProperties} 는
 * 스스로 빈이 되지 않는다 — 어딘가에서 {@code @EnableConfigurationProperties} 로 등록해야
 * 한다. 컴파일은 멀쩡히 통과하므로 <b>기동해 봐야 안다.</b>
 * {@code TranslateConfiguration}·{@code WeatherConfiguration} 이 같은 자리를 지킨다.
 *
 * <p>필드 기본값이 있어 설정 파일이 없어도 뜬다. 키가 비면 호출이 <b>명확한 실패</b>로
 * 끝날 뿐이다 — 기동은 막지 않는다.
 */
@Configuration
@EnableConfigurationProperties(MenuScanProperties.class)
public class MenuScanConfiguration {
}
