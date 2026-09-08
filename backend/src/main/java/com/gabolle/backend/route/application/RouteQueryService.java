package com.gabolle.backend.route.application;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.gabolle.backend.route.adapter.StraightLineRouteEstimator;
import com.gabolle.backend.route.domain.RouteLeg;
import com.gabolle.backend.route.domain.RouteQuery;
import com.gabolle.backend.route.domain.TravelMode;

/**
 * 두 좌표 사이의 경로를 답한다 — S15P21E201-62 · -184 · -189 · -196.
 *
 * <h2>이 서비스가 지키는 약속 하나</h2>
 *
 * 🔴 <b>어떤 경우에도 답을 준다.</b> 업체가 죽어도, 키가 없어도, 그 이동수단을 지원하지
 * 않아도 추정으로 답한다. 대신 <b>추정이라는 사실을 반드시 싣는다.</b> 부르는 쪽이
 * "경로를 못 구했을 때" 를 따로 다루지 않아도 되게 하려는 것이고, 그 대신 <b>"추정인지"</b>
 * 는 반드시 봐야 한다.
 *
 * <h2>순서</h2>
 *
 * <ol>
 *   <li>캐시에 있으면 그것 — 추정은 캐시에 안 들어간다({@link RouteCache})</li>
 *   <li>그 이동수단을 지원하는 업체가 있으면 물어본다</li>
 *   <li>못 받으면 직선거리 추정({@link StraightLineRouteEstimator})</li>
 * </ol>
 *
 * <h2>🔴 대중교통은 아직 진짜 경로가 아니다</h2>
 *
 * 카카오는 <b>자동차 경로만</b> 공개한다. 지하철·버스 경로를 주는 공개 API 가 없어서
 * 대중교통과 도보는 지금 전부 추정으로 나간다 — 그래서 {@code S15P21E201-184} 의 완료 기준
 * 중 "대중교통 응답에 환승 수와 단계별 안내가 들어 있다" 는 <b>이 작업에서 충족되지
 * 않는다.</b> 업체를 정하는 것이 먼저라 별도 티켓으로 나눴다.
 *
 * <p>충족되지 않은 것을 충족된 것처럼 적지 않는다. 대중교통 응답은 환승 수가 {@code null}
 * 이고 안내가 빈 목록이며 {@code estimated} 가 참이다 — 화면이 그것을 보고 "예상 소요시간"
 * 이라고 밝힐 수 있다.
 */
@Service
public class RouteQueryService {

	private static final Logger log = LoggerFactory.getLogger(RouteQueryService.class);

	static final String REASON_NO_PROVIDER = "이 이동수단의 경로를 물어볼 곳이 아직 없습니다.";

	static final String REASON_PROVIDER_FAILED = "경로 서비스에서 경로를 받지 못했습니다.";

	private final List<RouteProviderPort> providers;

	private final StraightLineRouteEstimator estimator;

	private final RouteCache cache;

	public RouteQueryService(List<RouteProviderPort> providers, StraightLineRouteEstimator estimator,
			RouteCache cache) {
		this.providers = List.copyOf(providers);
		this.estimator = estimator;
		this.cache = cache;
	}

	public RouteLeg find(RouteQuery query) {
		Optional<RouteLeg> cached = this.cache.find(query);
		if (cached.isPresent()) {
			return cached.get();
		}

		Optional<RouteProviderPort> provider = providerFor(query.mode());
		if (provider.isEmpty()) {
			return this.estimator.estimate(query, REASON_NO_PROVIDER);
		}

		Optional<RouteLeg> found = provider.get().find(query);
		if (found.isEmpty()) {
			// 🔴 좌표를 로그에 남기지 않는다 — 사용자가 어디에 있었는지가 로그에 쌓인다.
			log.info("경로 추정으로 대체 mode={} provider={}", query.mode(), provider.get().providerName());
			return this.estimator.estimate(query, REASON_PROVIDER_FAILED);
		}

		RouteLeg leg = found.get();
		this.cache.put(query, leg);
		return leg;
	}

	private Optional<RouteProviderPort> providerFor(TravelMode mode) {
		return this.providers.stream().filter(candidate -> candidate.supports(mode)).findFirst();
	}
}
