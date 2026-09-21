package com.gabolle.backend.place.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * {@link PlaceProperties} 를 스프링 컨텍스트에 올린다.
 *
 * <p>{@code no-db} 프로필에는 올리지 않는다. 이 설정을 쓰는 {@code NearbyPlaceService}·
 * {@code NearbyPlaceController} 가 같은 프로필 제약을 받으므로, 여기서만 프로필을 빼면 설정 빈은
 * 뜨는데 그걸 쓰는 서비스는 안 뜨는 반쪽짜리 상태가 된다.
 */
@Configuration
@Profile({ "db", "dev" })
@EnableConfigurationProperties(PlaceProperties.class)
public class PlaceConfiguration {
}
