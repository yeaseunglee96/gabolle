package com.gabolle.backend.route.walk;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.gabolle.backend.route.application.RouteProviderPort;
import com.gabolle.backend.route.config.RouteProperties;
import com.gabolle.backend.route.domain.RouteLeg;
import com.gabolle.backend.route.domain.RouteQuery;
import com.gabolle.backend.route.domain.TravelMode;

/**
 * 걷기를 우리 보행 그래프로 찾는다 — S15P21E201-1630.
 *
 * <p>🔴 왜. 걷는 구간을 경사 색으로 칠하려면(사용자 결정 2026-09-25) 길 모양이 있어야 하는데, 걷기를 물어볼 바깥
 * 업체가 없어 늘 직선 × 배수 어림이었다. 오픈스트리트맵 걷는 길로 서버 안에서 찾는다. 일정 조립의 걷는 시간도 같은
 * 창구({@code RouteQueryService})를 거치므로 같이 바뀐다 — 지도에 그린 길과 일정 시간이 같은 길에서 나온다.
 *
 * <p>그래프는 자원 {@value #RESOURCE}({@code bigData/process/walk-graph.mjs} 가 만든다, 옆의 {@code walk-graph.json}
 * 에 입력 해시가 있다)에서 읽는다. <b>기동을 늦추지 않게</b> 기동이 끝난 뒤 뒤에서 읽고, 그 전에 온 질문은 읽기가 끝날
 * 때까지 기다린다. 파일이 없거나 깨졌으면 한 번 경고를 남기고 빈 값을 준다 — 그러면 지금처럼 직선 어림으로 떨어진다.
 *
 * <p>못 붙이거나(출발·도착에서 300m 안에 걷는 길이 없다) 못 이으면 빈 값 — 역시 직선 어림으로 떨어진다.
 */
@Component
@Profile({ "db", "dev" })
public class WalkGraphRouteProvider implements RouteProviderPort {

	static final String RESOURCE = "geo/walk-graph.bin.gz";

	private static final Logger log = LoggerFactory.getLogger(WalkGraphRouteProvider.class);

	private final RouteProperties properties;

	private volatile WalkGraph graph;

	private volatile boolean unavailable;

	public WalkGraphRouteProvider(RouteProperties properties) {
		this.properties = properties;
	}

	@Override
	public boolean supports(TravelMode mode) {
		return mode == TravelMode.WALK;
	}

	@Override
	public String providerName() {
		return RouteLeg.PROVIDER_WALK_GRAPH;
	}

	@Override
	public Optional<RouteLeg> find(RouteQuery query) {
		if (!supports(query.mode())) {
			return Optional.empty();
		}
		WalkGraph loaded = graph();
		if (loaded == null) {
			return Optional.empty();
		}
		// 계단·급경사를 피하는 길을 원하면 그 비용으로 찾는다 — WalkGraph.route 의 stepFree 설명 참고.
		return loaded.route(query.originLat(), query.originLng(), query.destLat(), query.destLng(), query.stepFree())
				.map(route -> legOf(route, query.stepFree()));
	}

	RouteLeg legOf(WalkGraph.Route route, boolean stepFreeAsked) {
		int distanceM = (int) Math.round(route.meters());
		double speedKmh = this.properties.getWalkSpeedKmh() > 0 ? this.properties.getWalkSpeedKmh() : 4;
		int durationMin = Math.max(1, (int) Math.round(distanceM / (speedKmh * 1000.0 / 60.0)));
		if (stepFreeAsked && !route.stepFreeHonored()) {
			// 좌표를 남기지 않는다. 자주 뜨면 점 수 상한(WalkGraph.MAX_SETTLED)을 다시 봐야 한다는 신호다.
			log.info("계단 피하는 길을 못 찾아 가장 짧은 길로 답한다 — {}m", distanceM);
		}
		return new RouteLeg(TravelMode.WALK, distanceM, durationMin,
				null, null, null, // 걷는 데는 요금·통행료·환승이 없다
				false, null, RouteLeg.PROVIDER_WALK_GRAPH,
				route.path(), List.of(), null, route.pieces(),
				// 부탁이 없었으면 null — 「들어줬다」도 「못 들어줬다」도 아니다.
				stepFreeAsked ? Boolean.valueOf(route.stepFreeHonored()) : null);
	}

	/** 기동이 끝나면 뒤에서 읽기 시작한다 — 첫 걷기 질문이 읽기를 기다리지 않게. */
	@EventListener(ApplicationReadyEvent.class)
	void warmUp() {
		Thread loader = new Thread(this::graph, "walk-graph-load");
		loader.setDaemon(true);
		loader.start();
	}

	/** 한 번만 읽는다. 읽는 중에 온 질문은 여기서 기다린다. 못 읽으면 {@code null}. */
	synchronized WalkGraph graph() {
		if (this.graph != null || this.unavailable) {
			return this.graph;
		}
		long startedAt = System.nanoTime();
		Runtime runtime = Runtime.getRuntime();
		long usedBefore = runtime.totalMemory() - runtime.freeMemory();
		try (InputStream in = WalkGraphRouteProvider.class.getClassLoader().getResourceAsStream(RESOURCE)) {
			if (in == null) {
				throw new IOException(RESOURCE + " 가 자원에 없다");
			}
			this.graph = WalkGraph.read(in);
		}
		catch (IOException | RuntimeException ex) {
			this.unavailable = true;
			log.warn("보행 그래프를 못 읽어 걷기는 직선 어림으로 답한다 — {}", ex.toString());
			return null;
		}
		long usedAfter = runtime.totalMemory() - runtime.freeMemory();
		log.info("보행 그래프를 읽었다 — 점 {} · 길 {} · 한 덩어리 점 {} · {}ms · 힙 증가 약 {}MB",
				this.graph.nodeCount(), this.graph.wayCount(), this.graph.mainNodeCount(),
				(System.nanoTime() - startedAt) / 1_000_000, Math.max(0, (usedAfter - usedBefore) / 1_000_000));
		return this.graph;
	}
}
