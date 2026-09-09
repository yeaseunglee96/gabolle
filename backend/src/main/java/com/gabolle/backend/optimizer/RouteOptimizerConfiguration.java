package com.gabolle.backend.optimizer;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * {@link RouteOptimizerProperties} 를 빈으로 연결한다. 다른 {@code *Configuration} 클래스와
 * 같은 패턴이다(예: {@code StorageConfiguration}).
 */
@Configuration
@EnableConfigurationProperties(RouteOptimizerProperties.class)
public class RouteOptimizerConfiguration {
}
