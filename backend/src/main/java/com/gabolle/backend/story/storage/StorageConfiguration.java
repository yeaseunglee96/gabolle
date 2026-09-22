package com.gabolle.backend.story.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** {@link StorageProperties} 를 빈으로 연결한다. */
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfiguration {
}
