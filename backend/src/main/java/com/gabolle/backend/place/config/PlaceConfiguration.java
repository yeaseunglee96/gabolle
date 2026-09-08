package com.gabolle.backend.place.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * {@link PlaceProperties} 를 스프링 컨텍스트에 올린다.
 *
 * <p>🔴 {@code no-db} 프로필에는 올리지 않는다. 그 프로필에는 JPA 빈 자체가 없어서, 이 설정을
 * 물고 있는 {@code NearbyPlaceService}·{@code NearbyPlaceController} 도 같은
 * {@code @Profile({"db","dev"})} 제약을 받는다. 여기서만 프로필을 빼면 설정 빈은 뜨는데 그걸
 * 쓰는 서비스는 안 뜨는 반쪽짜리 상태가 되고, 그 어긋남은 배포에서만 드러난다.
 */
@Configuration
@Profile({ "db", "dev" })
@EnableConfigurationProperties(PlaceProperties.class)
public class PlaceConfiguration {
}
