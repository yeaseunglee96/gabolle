package com.gabolle.backend.route;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.util.function.Supplier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.web.client.RestClient;

import com.gabolle.backend.route.adapter.KakaoMobilityRouteAdapter;
import com.gabolle.backend.route.adapter.StraightLineRouteEstimator;
import com.gabolle.backend.route.application.RouteCache;
import com.gabolle.backend.route.application.RouteQueryService;
import com.gabolle.backend.route.presentation.RouteController;

import tools.jackson.databind.ObjectMapper;

/**
 * 🔴 스프링이 이 패키지의 빈을 <b>실제로 만들 수 있는지</b> 확인한다 — S15P21E201-184.
 *
 * <h2>왜 이 검사가 생겼나</h2>
 *
 * 2026-09-08 에 이 패키지 때문에 <b>운영이 두 번 내려갔다.</b> {@code KakaoMobilityRouteAdapter}
 * 에 테스트용 생성자를 하나 더 두었는데, 스프링은 {@code private} 이 아닌 생성자를 전부
 * 후보로 보므로 "생성자가 하나뿐" 판정이 깨져 기본(무인자) 생성자를 찾다가 <b>기동 자체가
 * 죽었다.</b> 고친 방법은 운영용 생성자에 {@code @Autowired} 를 붙이는 것이다.
 *
 * <h2>🔴 왜 기존 검사들이 하나도 못 잡았나 — 이게 진짜 교훈이다</h2>
 *
 * 이 패키지의 다른 검사 36개는 전부 초록이었고 CI 도 초록이었다. 그것들이 빈을
 * <b>손으로 {@code new} 해서</b> 만들기 때문이다. 손으로 만들면 생성자를 내가 고르므로,
 * <b>스프링이 어느 생성자를 고를지는 한 번도 확인되지 않는다.</b>
 *
 * <p>컨텍스트를 띄우는 검사가 저장소에 하나 있지만({@code RestClientBuilderContextTest})
 * 그것도 못 잡는다 — 기본 프로필이 {@code no-db} 이고 이 어댑터는
 * {@code @Profile({"db","dev"})} 라서 <b>그 컨텍스트에서는 만들어지지도 않는다.</b>
 *
 * <p>그래서 이 검사는 프로필을 {@code dev} 로 켜고 이 패키지를 실제로 스캔한다.
 * DB 는 안 쓴다 — 이 패키지에 표를 읽는 코드가 없어서 붙일 이유가 없고, DB 를 붙이면
 * 이 검사가 느려지고 DB 가 없는 곳에서 건너뛰어진다. <b>건너뛰는 안전장치는 없는 것과
 * 같다</b>는 것이 오늘의 교훈이기도 하다.
 */
class RouteBeanWiringTest {

	@Test
	@DisplayName("🔴 스프링이 경로 패키지의 빈을 전부 만들 수 있다 — 손으로 new 하는 검사는 이걸 못 본다")
	void springCanInstantiateEveryRouteBean() {
		try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
			// 어댑터는 @Profile({"db","dev"}) 다. 프로필을 안 켜면 만들어지지 않고,
			// 그러면 이 검사는 아무것도 안 보면서 초록이 된다.
			context.getEnvironment().setActiveProfiles("dev");

			// 이 패키지 밖에서 오는 것들. 운영에서는 스프링 부트가 자동으로 준다.
			context.registerBean(RestClient.Builder.class, (Supplier<RestClient.Builder>) RestClient::builder);
			context.registerBean(ObjectMapper.class, (Supplier<ObjectMapper>) ObjectMapper::new);
			context.registerBean(Clock.class, (Supplier<Clock>) Clock::systemUTC);

			context.scan("com.gabolle.backend.route");
			context.refresh();

			// 🔴 어댑터가 이 검사의 이유다. 나머지도 함께 본다 — 생성자가 하나뿐이라
			//    지금은 안전하지만, 누군가 같은 이유로 하나를 더 만들면 그때 여기서 걸린다.
			assertThat(context.getBean(KakaoMobilityRouteAdapter.class)).isNotNull();
			assertThat(context.getBean(StraightLineRouteEstimator.class)).isNotNull();
			assertThat(context.getBean(RouteCache.class)).isNotNull();
			assertThat(context.getBean(RouteQueryService.class)).isNotNull();
			assertThat(context.getBean(RouteController.class)).isNotNull();
		}
	}

	@Test
	@DisplayName("경로 업체 목록에 카카오 어댑터가 실제로 들어간다 — 빈이 떠도 목록에 안 잡히면 늘 추정이 나간다")
	void theKakaoAdapterActuallyReachesTheService() {
		try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
			context.getEnvironment().setActiveProfiles("dev");
			context.registerBean(RestClient.Builder.class, (Supplier<RestClient.Builder>) RestClient::builder);
			context.registerBean(ObjectMapper.class, (Supplier<ObjectMapper>) ObjectMapper::new);
			context.registerBean(Clock.class, (Supplier<Clock>) Clock::systemUTC);
			context.scan("com.gabolle.backend.route");
			context.refresh();

			// 서비스는 List<RouteProviderPort> 를 주입받아 이동수단으로 고른다. 어댑터가
			// 그 목록에 안 들어가면 기동은 멀쩡한데 자차 경로가 전부 추정으로 나간다 —
			// 아무도 안 죽으니 아무도 모르는 종류의 고장이다.
			assertThat(context.getBeansOfType(com.gabolle.backend.route.application.RouteProviderPort.class).values())
					.anyMatch(KakaoMobilityRouteAdapter.class::isInstance);
		}
	}
}
