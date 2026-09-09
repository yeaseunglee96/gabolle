package com.gabolle.backend.story.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * {@link StorageProperties} 를 빈으로 연결한다. 다른 {@code *Configuration} 클래스와 같은 패턴이다
 * (예: {@code PlaceConfiguration}).
 */
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfiguration {
}
