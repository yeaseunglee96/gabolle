package com.gabolle.backend.place.photo.proxy;

import java.net.InetAddress;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.gabolle.backend.story.storage.StoragePort;

/** 공공 사진 대리 조회를 스프링에 연결한다(S15P21E201-1954). 저장소가 있는 프로필에서만 뜬다. */
@Configuration
@Profile({ "db", "dev" })
@EnableConfigurationProperties(ImageProxyProperties.class)
public class ImageProxyConfiguration {

	@Bean
	ImageProxyService imageProxyService(ImageProxyProperties properties, StoragePort storage) {
		ImageUrlGuard guard = new ImageUrlGuard(properties.getAllowedHosts(), InetAddress::getAllByName);
		return new ImageProxyService(guard, new HttpImageFetcher(properties), storage, properties);
	}
}
