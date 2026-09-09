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
 * 같은 경로를 다시 묻지 않게 답을 들고 있는 자리 — S15P21E201-196.
 *
 * <h2>🔴 열쇠를 좌표 그대로 쓰지 않는다</h2>
 *
 * 소수점 아래를 그대로 열쇠로 쓰면 <b>같은 경로가 한 번도 안 맞는다.</b> 지도에서 손가락을
 * 조금만 움직여도 좌표 끝자리가 바뀌는데, 그건 사람에게 같은 장소다. 그래서 소수점 넷째
 * 자리까지만 남긴다 — 위도 0.0001° 는 약 11m 이고, 그 정도 차이로 경로가 달라지지 않는다.
 *
 * <p>반대로 너무 뭉개면 다른 경로가 같은 답을 받는다. 넷째 자리는 그 사이에서 고른 값이고,
 * 정답이 아니라 <b>고른 값</b>이다.
 *
 * <h2>왜 DB 나 Redis 가 아닌가</h2>
 *
 * 경로는 <b>잃어도 되는 값</b>이다. 없으면 다시 물으면 그만이고, 다시 묻는 값이 비싸지도
 * 않다. 표를 만들면 마이그레이션·정리 배치·용량 관리가 따라오고, Redis 를 넣으면 배포에
 * 컨테이너가 하나 는다. 지금 서버가 한 대라 메모리 하나로 충분하다.
 *
 * <p>🔴 <b>서버가 여러 대가 되면 이 판단이 틀린다</b> — 각 대가 자기 사본을 들고 있어 적중률이
 * 대수만큼 떨어진다. 그때 갈아탈 자리가 이 클래스 하나라는 것이 지금 이 모양의 값이다.
 *
 * <h2>추정은 담지 않는다</h2>
 *
 * 🔴 {@code estimated=true} 인 답은 캐시하지 않는다. 추정은 <b>실패한 흔적</b>이라, 담아 두면
 * 업체가 되살아난 뒤에도 남은 시간 동안 계속 추정이 나간다. 실패를 오래 기억하는 캐시는
 * 장애를 늘린다.
 */
@Component
public class RouteCache {

	private final RouteProperties properties;

	private final Clock clock;

	/**
	 * 🔴 접근 순서(access-order) {@link LinkedHashMap} 이다 — 넘치면 <b>가장 오래 안 쓴 것</b>부터
	 * 버린다. 넣은 순서로 버리면 자주 쓰는 경로가 먼저 밀려난다.
	 *
	 * <p>{@code ConcurrentHashMap} 이 아니라 통째로 잠근다. 담기는 것이 수천 개고 다루는 시간이
	 * 아주 짧아서 잠금 경쟁이 문제가 되지 않는다 — 여기서 잘못 만든 잠금 없는 자료구조가
	 * 만드는 버그가 훨씬 비싸다.
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

	/** 추정({@code estimated=true})은 담지 않는다 — 클래스 javadoc 참고. */
	public void put(RouteQuery query, RouteLeg leg) {
		if (leg.estimated()) {
			return;
		}
		Instant expiresAt = Instant.now(this.clock).plus(this.properties.getCacheTtl());
		synchronized (this.entries) {
			this.entries.put(keyOf(query), new Entry(leg, expiresAt));
		}
	}

	/** 열쇠 — 이동수단과 소수점 넷째 자리까지의 출발·도착 좌표. */
	static String keyOf(RouteQuery query) {
		return query.mode().name()
				+ '|' + round(query.originLat()) + ',' + round(query.originLng())
				+ '|' + round(query.destLat()) + ',' + round(query.destLng());
	}

	private static String round(double value) {
		return String.format("%.4f", value);
	}

	private record Entry(RouteLeg leg, Instant expiresAt) {
	}
}
