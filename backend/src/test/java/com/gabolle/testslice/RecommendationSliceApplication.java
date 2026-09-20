package com.gabolle.testslice;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 통합 테스트가 띄우는 애플리케이션 — 추천·이벤트·공통만 올린다.
 *
 * <p>전체({@code GabolleBackendApplication})를 띄우면 다른 도메인이 반쯤 만들어 둔 빈까지 살아나야
 * 이 테스트가 돈다. 그러면 추천과 상관없는 이유로 빨개지고 빨간불의 뜻이 사라진다. 애플리케이션
 * 전체가 뜨는지는 {@code GabolleBackendApplicationTests} 가 따로 본다.
 *
 * <p>좁힌 것은 스캔 범위뿐이다 — 자동 설정도 Flyway 도 그대로라 JSONB·UUID·배열·DB 제약 검증은
 * 안 줄었다.
 *
 * <p>{@code com.gabolle.backend} 밖에 두는 것이 필수다. 테스트 소스도 클래스패스에 올라가므로 안에
 * 두면 본 애플리케이션 스캔에 걸려 아래 {@code @EnableJpaRepositories} 가 {@code no-db} 프로필에서도
 * 켜지고, EntityManagerFactory 가 없어 {@code contextLoads} 가 죽는다.
 *
 * <p>{@code @TestConfiguration} 으로 스캔에서 빼는 방법은 안 된다 — Boot 는
 * {@code @SpringBootTest(classes=...)} 에 테스트 컴포넌트만 있으면 {@code @SpringBootConfiguration}
 * 을 따로 찾아 덧붙여서, 본 앱이 통째로 다시 올라온다. 패키지를 옮기는 것이 답이다.
 */
@SpringBootApplication(scanBasePackages = {
		"com.gabolle.backend.common",
		"com.gabolle.backend.event",
		"com.gabolle.backend.recommendation",
		// 추천 엔진이 기대는 곳만 더한다. 엔진에서 @ConditionalOnBean 을 걷어냈으므로 배선은
		// 이 목록이 정한다.
		//
		// trip 은 패키지 전체가 아니라 infra 만 올린다. 엔진이 쓰는 것은 저장소 둘
		// (TripRepository · TripSeedPlaceRepository)뿐인데, trip 전체를 올리면 TripQueryService 가
		// 서고 그것을 조건으로 삼는 RecommendationJobRunner 와 결과 조회·컨트롤러가 줄줄이 켜져
		// itinerary 까지 따라온다.
		"com.gabolle.backend.place",
		"com.gabolle.backend.trip.infra",
		// 테마 설정과 ThemeWeightResolver 가 여기 있다. 추천이 테마별 가중치를 먹이려면 필요하다.
		"com.gabolle.backend.coursetheme"
})
@EntityScan(basePackages = {
		"com.gabolle.backend.event.domain",
		"com.gabolle.backend.recommendation.domain",
		"com.gabolle.backend.place.domain",
		"com.gabolle.backend.trip.infra",
		// EventIngestService 가 행동 이벤트를 적기 전에 행동 개인화 동의를 보므로 AppUserRepository
		// 를 요구한다. 매핑과 저장소가 없으면 컨텍스트가 안 뜬다.
		"com.gabolle.backend.user.domain"
})
@EnableJpaRepositories(basePackages = {
		"com.gabolle.backend.event.repository",
		"com.gabolle.backend.recommendation.repository",
		"com.gabolle.backend.place.repository",
		"com.gabolle.backend.trip.infra",
		"com.gabolle.backend.user.repository"
})
public class RecommendationSliceApplication {
}
