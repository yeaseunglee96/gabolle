package com.gabolle.backend.place.photo.proxy;

import java.net.InetAddress;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.gabolle.backend.story.storage.StoragePort;

/**
 * 공공 사진 대리 조회를 스프링에 연결한다(S15P21E201-1954).
 *
 * <p>저장소는 있으면 쓴다 — 일부 시험용 축소 앱(TripSliceApplication 등)은 이 패키지를 훑지만
 * 저장소 빈이 없다. 없으면 저장 없이 매번 받아 넘긴다.
 */
@Configuration
@Profile({ "db", "dev" })
@EnableConfigurationProperties(ImageProxyProperties.class)
public class ImageProxyConfiguration {

	@Bean
	ImageProxyService imageProxyService(ImageProxyProperties properties, ObjectProvider<StoragePort> storage) {
		ImageUrlGuard guard = new ImageUrlGuard(properties.getAllowedHosts(), InetAddress::getAllByName);
		return new ImageProxyService(guard, new HttpImageFetcher(properties), storage.getIfAvailable(), properties);
	}
}
