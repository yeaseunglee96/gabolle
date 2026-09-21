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
 * 두 좌표 사이의 경로를 답한다. 어떤 경우에도 답을 준다 — 업체가 죽어도, 키가 없어도,
 * 그 이동수단을 지원하지 않아도 추정으로 답하고 추정이라는 사실을 싣는다. 부르는 쪽은
 * 실패를 따로 다루지 않아도 되는 대신 estimated 는 반드시 봐야 한다.
 *
 * 순서는 캐시(추정은 안 들어간다) → 그 이동수단을 지원하는 업체 → 직선거리 추정이다.
 *
 * 대중교통과 도보는 지금 전부 추정으로 나간다 — 경로를 주는 공개 API 가 없다. 그래서
 * 대중교통 응답은 환승 수가 null, 안내가 빈 목록, estimated 가 참이다.
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
			// 좌표를 로그에 남기지 않는다 — 사용자가 어디에 있었는지가 로그에 쌓인다.
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
