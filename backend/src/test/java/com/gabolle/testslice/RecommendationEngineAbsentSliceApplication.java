package com.gabolle.testslice;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import com.gabolle.backend.recommendation.adapter.BaselineCandidateTranslator;
import com.gabolle.backend.recommendation.adapter.BaselineEngineStartupValidator;
import com.gabolle.backend.recommendation.adapter.BaselineRecommendationEngine;

/**
 * 추천 엔진 빈이 없는 컨텍스트 — S15P21E201-808.
 *
 * <h2>분리 이유</h2>
 * {@link RecommendationSliceApplication} 이 {@code place} 와 {@code trip.infra} 를 올리기
 * 시작하면서 그 슬라이스에는 {@code BaselineRecommendationEngine} 이 실제로 선다. 그래서
 * "엔진이 없는 배포" 를 재는 검사가 그 슬라이스에서는 성립하지 않는다.
 *
 * <h2>제외 목록이 명시적인 이유</h2>
 * 엔진 셋을 스캔에서 빼는 방식으로 그 상태를 만든다. {@code place} 를 안 올리는 것만으로는
 * 안 된다 — 엔진에서 {@code @ConditionalOnBean} 을 걷어냈으므로 이제 그 빈들은 조건 없이
 * 만들어지려 하고, 의존 대상이 없으면 컨텍스트가 뜨지 못한다. 그것이 이 티켓이 바꾼 계약이다.
 * 추천 패키지를 {@code db} 로 올리는 배포는 {@code place} 도 함께 올려야 한다.
 *
 * <p>제외 대상을 클래스 이름으로 적어 두면 엔진 구성이 바뀔 때 컴파일이 먼저 알려 준다.
 *
 * <h2>{@code @SpringBootApplication} 을 안 쓴 이유</h2>
 * 그 애노테이션에는 제외 필터를 넘길 자리가 없다. 아래 셋이 그것을 풀어 쓴 것과 같다.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(basePackages = {
		"com.gabolle.backend.common",
		"com.gabolle.backend.event",
		"com.gabolle.backend.recommendation"
}, excludeFilters = {
		// @SpringBootApplication 이 기본으로 다는 둘. 직접 @ComponentScan 을 쓰면 사라지므로
		// 여기 적는다. 앞의 것이 없으면 @TestConfiguration 이 스캔에 걸려 대역 엔진이
		// 딸려 들어오고, 그러면 "엔진이 없는" 컨텍스트가 아니게 된다.
		@ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
		@ComponentScan.Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class),
		@ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
				BaselineRecommendationEngine.class,
				BaselineCandidateTranslator.class,
				BaselineEngineStartupValidator.class
		})
})
@EntityScan(basePackages = {
		"com.gabolle.backend.event.domain",
		"com.gabolle.backend.recommendation.domain"
})
@EnableJpaRepositories(basePackages = {
		"com.gabolle.backend.event.repository",
		"com.gabolle.backend.recommendation.repository"
})
public class RecommendationEngineAbsentSliceApplication {
}
