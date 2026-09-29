package com.gabolle.backend.route.application;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.gabolle.backend.route.config.RouteProperties;
import com.gabolle.backend.route.domain.RouteLeg;
import com.gabolle.backend.route.domain.RouteQuery;

/**
 * 같은 경로를 다시 묻지 않게 답을 들고 있는 자리.
 *
 * 열쇠의 좌표는 소수점 넷째 자리까지만 쓴다. 위도 0.0001° 는 약 11m 라 그만큼 차이로는
 * 경로가 달라지지 않고, 그대로 쓰면 손가락이 조금만 움직여도 캐시가 한 번도 안 맞는다.
 * 더 뭉개면 다른 경로가 같은 답을 받는다.
 *
 * 프로세스 메모리에만 둔다. 서버가 한 대라 충분하고, 여러 대가 되면 각 대가 자기 사본을
 * 들고 있어 적중률이 대수만큼 떨어진다.
 *
 * estimated=true 인 답은 담지 않는다. 담으면 업체가 되살아난 뒤에도 TTL 동안 추정이 나간다.
 */
@Component
public class RouteCache {

	private final RouteProperties properties;

	private final Clock clock;

	/**
	 * 접근 순서(access-order) LinkedHashMap 이라 넘치면 가장 오래 안 쓴 것부터 버린다.
	 * 동시성은 맵 통째로 잠가서 낸다 — 담기는 것이 수천 개고 다루는 시간이 짧아 경쟁이 없다.
	 */
	private final Map<String, Entry> entries;

	public RouteCache(RouteProperties properties, Clock clock) {
		this.properties = properties;
		this.clock = clock;
		int max = Math.max(1, properties.getCacheMaxEntries());
		this.entries = new LinkedHashMap<>(16, 0.75f, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
				return size() > max;
			}
		};
	}

	public Optional<RouteLeg> find(RouteQuery query) {
		String key = keyOf(query);
		synchronized (this.entries) {
			Entry entry = this.entries.get(key);
			if (entry == null) {
				return Optional.empty();
			}
			if (Instant.now(this.clock).isAfter(entry.expiresAt())) {
				// 지난 것은 그 자리에서 버린다 — 안 버리면 상한이 지난 값으로 채워진다.
				this.entries.remove(key);
				return Optional.empty();
			}
			return Optional.of(entry.leg());
		}
	}

	/** 추정(estimated=true)은 담지 않는다. */
	public void put(RouteQuery query, RouteLeg leg) {
		if (leg.estimated()) {
			return;
		}
		Instant expiresAt = Instant.now(this.clock).plus(this.properties.getCacheTtl());
		synchronized (this.entries) {
			this.entries.put(keyOf(query), new Entry(leg, expiresAt));
		}
	}

	/**
	 * 열쇠 — 이동수단과 소수점 넷째 자리까지의 출발·도착 좌표, 그리고 계단을 피하는 길인가.
	 *
	 * <p>🔴 계단을 피하는 길({@code stepFree})은 열쇠를 따로 쓴다. 같이 쓰면 먼저 물은 사람의 계단 길이 휠체어
	 * 사용자에게 그대로 나간다. 보통 길의 열쇠 모양은 전과 같다 — 붙이는 것은 stepFree 일 때뿐이다.
	 */
	static String keyOf(RouteQuery query) {
		return query.mode().name()
				+ '|' + round(query.originLat()) + ',' + round(query.originLng())
				+ '|' + round(query.destLat()) + ',' + round(query.destLng())
				+ (query.stepFree() ? "|stepFree" : "");
	}

	private static String round(double value) {
		return String.format("%.4f", value);
	}

	private record Entry(RouteLeg leg, Instant expiresAt) {
	}
}
