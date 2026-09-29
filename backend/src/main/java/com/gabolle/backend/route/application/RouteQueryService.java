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
 *
 * 🔴 대중교통을 물어도 <b>걷는 편이 빠르면 도보로 답한다</b> (S15P21E201-1564). 노선망 탐색은
 * 「그냥 걸어가기」와 견주지 않아서, 650m 떨어진 두 식당 사이가 배차 대기를 얹은 178분으로
 * 나왔다(운영 실측 2026-09-24). 그 한 구간이 하루 시각을 통째로 밀고 「하루 넘길 위험」을 띄웠다.
 * 어느 길찾기든 걷기가 이기면 걷기를 보여 준다 — 사람도 그렇게 간다.
 */
@Service
public class RouteQueryService {

	private static final Logger log = LoggerFactory.getLogger(RouteQueryService.class);

	static final String REASON_NO_PROVIDER = "이 이동수단의 경로를 물어볼 곳이 아직 없습니다.";

	static final String REASON_PROVIDER_FAILED = "경로 서비스에서 경로를 받지 못했습니다.";

	static final String REASON_WALK_FASTER = "걸어가는 편이 대중교통보다 빨라 도보로 계산했습니다.";

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
		RouteLeg leg = lookup(query);
		return query.mode() == TravelMode.TRANSIT ? walkIfFaster(query, leg) : leg;
	}

	/**
	 * 대중교통 답과 걷기를 견줘 빠른 쪽을 준다. 같으면 도보다 — 기다리지도 갈아타지도 않는다.
	 * 요금·환승은 싣지 않는다(걷는 데는 요금이 없다).
	 *
	 * <p>🔴 걷기는 도보를 물었을 때와 같은 길(보행 그래프)로 잰다. 예전에는 직선 × 우회 배수 어림과 견줬는데,
	 * 강·철길·산을 돌아가야 하는 곳은 직선으로는 가깝고 실제로 걸으면 멀다. 그러면 30분 버스를 버리고 실제로는
	 * 50분 걷는 길을 「걷는 편이 빠르다」며 내줬다. 계단을 피하는 길({@code stepFree})을 물었으면 그것도 넘긴다.
	 *
	 * <p>보행 그래프가 답을 못 줄 때(자원이 없다·길에 못 붙인다)만 전처럼 직선 어림으로 견주고, 그때는
	 * {@link #REASON_WALK_FASTER} 를 단 추정으로 나간다. 실제 길로 이긴 걷기는 잰 값이라 추정 표시가 없다 —
	 * 대중교통을 물었는데 이동수단이 WALK 로 온 것이 바꿔 준 표시다.
	 *
	 * <p>직선으로 곧장 걸어도 대중교통보다 느리면 길을 찾지 않는다 — 실제 걷기는 직선보다 짧을 수 없어 결과가
	 * 같고, 먼 구간마다 그래프를 뒤지지 않아도 된다.
	 */
	private RouteLeg walkIfFaster(RouteQuery query, RouteLeg transit) {
		RouteQuery walkQuery = new RouteQuery(query.originLat(), query.originLng(),
				query.destLat(), query.destLng(), TravelMode.WALK, query.departureAt(), query.stepFree());
		if (this.estimator.straightWalkMinutesFloor(walkQuery) > transit.durationMin()) {
			return transit;
		}
		RouteLeg walk = realWalk(walkQuery)
				.orElseGet(() -> this.estimator.estimate(walkQuery, REASON_WALK_FASTER));
		return walk.durationMin() <= transit.durationMin() ? walk : transit;
	}

	/** 도보 질문과 같은 길(캐시 → 걷기 업체)로 잰 실제 걷기. 업체가 없거나 못 주면 빈 값 — 어림은 부르는 쪽이 한다. */
	private Optional<RouteLeg> realWalk(RouteQuery walkQuery) {
		Optional<RouteLeg> cached = this.cache.find(walkQuery);
		if (cached.isPresent()) {
			return cached;
		}
		Optional<RouteLeg> found = providerFor(TravelMode.WALK).flatMap(provider -> provider.find(walkQuery));
		found.ifPresent(leg -> this.cache.put(walkQuery, leg));
		return found;
	}

	private RouteLeg lookup(RouteQuery query) {
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
