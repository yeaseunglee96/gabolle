package com.gabolle.backend.place.photo;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/** Google 장소 사진 대리 조회를 스프링에 연결한다. 키가 없어도 뜬다. */
@Configuration
@EnableConfigurationProperties(GooglePlacePhotoProperties.class)
public class GooglePlacePhotoConfiguration {

	@Bean
	GooglePlacePhotoClient googlePlacePhotoClient(RestClient.Builder builder, GooglePlacePhotoProperties properties) {
		return new GooglePlacePhotoClient(builder, properties);
	}
}
