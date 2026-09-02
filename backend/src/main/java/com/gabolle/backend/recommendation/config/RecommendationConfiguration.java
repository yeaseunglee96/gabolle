package com.gabolle.backend.recommendation.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** {@link RecommendationProperties} 를 빈으로 만든다. */
@Configuration
@EnableConfigurationProperties(RecommendationProperties.class)
public class RecommendationConfiguration {
}
