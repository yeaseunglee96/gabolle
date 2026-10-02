package com.gabolle.backend.transit.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.gabolle.backend.transit.config.TransitProperties;
import com.gabolle.backend.transit.domain.BusArrival;
import com.gabolle.backend.transit.domain.NearbyBusArrivalsQuery;
import com.gabolle.backend.transit.domain.NearbyBusArrivalsResult;
import com.gabolle.backend.transit.domain.NearbyBusStop;
import com.gabolle.backend.transit.domain.StopArrivals;

import tools.jackson.databind.ObjectMapper;

/**
 * 좌표 근처의 정류소와 각 정류소의 실시간 버스 도착정보를 조회한다.
 * 근처 정류소를 찾은 뒤 가까운 순으로 {@code properties.maxStops}개를 골라 정류소마다
 * 도착정보를 따로 조회한다 — 정류소 하나당 벤더 호출이 하나씩 더 나간다.
 * 실시간 정보라 캐시하지 않는다. 1분 전 도착 예정 시간은 그 자체로 틀린 값이다.
 * 그 {@code maxStops} 곳 가운데 하나의 조회가 실패하면 전체 요청이 실패한다 — 일부만 빼고 답하면 "이
 * 근처엔 버스가 이것뿐" 이라고 오해하게 된다.
 *
 * <p>🔴 S15P21E201-1755 — 가까운 곳 가운데 「오는 버스 없음」이 있으면 그다음 정류소를 {@code candidateStops} 곳까지
 * 「덤」으로 더 보고, 도착이 있는 곳을 앞으로 올려 {@code maxStops} 곳을 돌려준다. 전에는 가장 가까운 3곳이 모두 비면 바로
 * 옆 정류소에 버스가 와도 「지금 오는 버스가 없어요」였다(시연 점검 — 광안리). 돌려주는 수는 그대로다 — 앱이 받은 정류소를
 * 모두 카드로 그린다. 덤은 짧은 제한으로 부르고, 실패하거나 늦으면 버린다 — 앞 곳들의 보장은 그대로다.
 *
 * <p>🔴 S15P21E201-1956 — 운영(10/2) 502 의 원인은 업체의 근처 정류소 호출이 1.3~5초로 느려 읽기 제한(5초)에 걸린 것이었다.
 * 정류소 목록은 움직이지 않으니 자리(약 50m 칸)별로 {@code stopsCacheTtl} 동안 담아 두고, 실패하면 전에 받은 목록으로 답한다.
 * 도착은 {@code arrivalsFreshTtl} 동안 다시 부르지 않고, 호출이 실패하면 {@code arrivalsStaleTtl} 안에 받은 값으로 답한다.
 * 그보다 오래된 도착 시간은 틀린 값이라 쓰지 않는다 — 위의 「실시간이라 캐시하지 않는다」는 이 짧은 창 밖의 이야기다.
 */
@Service
@Profile({ "db", "dev" })
public class TransitService {

	private final TransitVendorPort vendor;

	private final TransitProperties properties;

	private final ObjectMapper objectMapper;

	private final Clock clock;

	/** 자리 칸 → 근처 정류소 목록. 칸은 위도·경도를 {@link #CELL_DEGREES} 로 나눈 정수 둘이다. */
	private final Map<String, Stamped<List<NearbyBusStop>>> stopsCache = new ConcurrentHashMap<>();

	/** 「도시코드/정류소」 → 마지막으로 받은 도착 목록. */
	private final Map<String, Stamped<List<BusArrival>>> arrivalsCache = new ConcurrentHashMap<>();

	/** 약 50m. 그 안에서는 근처 정류소가 같다고 본다. */
	static final double CELL_DEGREES = 0.0005;

	/** 담아 두는 칸 수 상한 — 넘으면 오래된 것부터 버린다(서버 메모리 보호). */
	static final int MAX_ENTRIES = 5000;

	@Autowired
	public TransitService(TransitVendorPort vendor, TransitProperties properties, ObjectMapper objectMapper) {
		this(vendor, properties, objectMapper, Clock.systemUTC());
	}

	TransitService(TransitVendorPort vendor, TransitProperties properties, ObjectMapper objectMapper, Clock clock) {
		this.vendor = vendor;
		this.properties = properties;
		this.objectMapper = objectMapper;
		this.clock = clock;
	}

	public NearbyBusArrivalsResult nearbyArrivals(NearbyBusArrivalsQuery query) {
		List<NearbyBusStop> nearbyStops = nearbyStops(query.lat(), query.lng());

		int limit = Math.min(nearbyStops.size(), this.properties.getMaxStops());
		List<StopArrivals> seen = new ArrayList<>(nearbyStops.subList(0, limit).stream()
				.map(this::withArrivals)
				.toList());

		// 덤 — 도착 있는 곳이 돌려줄 수만큼 찰 때까지만 더 본다. 가까운 곳이 모두 차 있으면 한 번도 안 부른다.
		int look = Math.min(nearbyStops.size(), Math.max(limit, this.properties.getCandidateStops()));
		for (int i = limit; i < look && withBuses(seen) < limit; i++) {
			NearbyBusStop extra = nearbyStops.get(i);
			try {
				String json = this.vendor.fetchExtraArrivalsJson(extra.cityCode(), extra.nodeId());
				seen.add(new StopArrivals(extra, TagoArrivalsJsonParser.parse(json, this.objectMapper)));
			}
			catch (TransitVendorException dropped) {
				// 덤이다 — 실패하거나 짧은 제한에 걸리면 버리고 가까운 곳들로 답한다(업체 호출 실패는 어댑터가 남긴다).
			}
		}

		// 도착 있는 곳 먼저, 그 안에서는 가까운 순. 그다음 빈 곳을 가까운 순으로 채운다.
		List<StopArrivals> ordered = new ArrayList<>(seen.size());
		seen.stream().filter(TransitService::hasBuses).forEach(ordered::add);
		seen.stream().filter((s) -> !hasBuses(s)).forEach(ordered::add);
		return new NearbyBusArrivalsResult(List.copyOf(ordered.subList(0, Math.min(limit, ordered.size()))));
	}

	private List<NearbyBusStop> nearbyStops(double lat, double lng) {
		String key = Math.round(lat / CELL_DEGREES) + ":" + Math.round(lng / CELL_DEGREES);
		Instant now = this.clock.instant();
		Stamped<List<NearbyBusStop>> cached = this.stopsCache.get(key);
		if (cached != null && cached.youngerThan(now, this.properties.getStopsCacheTtl())) {
			return cached.value();
		}
		try {
			List<NearbyBusStop> stops = TagoNearbyStopsJsonParser.parse(this.vendor.fetchNearbyStopsJson(lat, lng),
					this.objectMapper);
			if (!stops.isEmpty()) {
				put(this.stopsCache, key, new Stamped<>(stops, now));
			}
			return stops;
		}
		catch (TransitVendorException exception) {
			// 정류소는 움직이지 않는다 — 오래된 목록이라도 있으면 그것으로 답한다. 키 거절은 그대로 올린다.
			if (cached != null && !isKeyRejected(exception)) {
				return cached.value();
			}
			throw exception;
		}
	}

	private StopArrivals withArrivals(NearbyBusStop stop) {
		String key = stop.cityCode() + "/" + stop.nodeId();
		Instant now = this.clock.instant();
		Stamped<List<BusArrival>> cached = this.arrivalsCache.get(key);
		if (cached != null && cached.youngerThan(now, this.properties.getArrivalsFreshTtl())) {
			return new StopArrivals(stop, cached.value());
		}
		try {
			String arrivalsJson = this.vendor.fetchArrivalsJson(stop.cityCode(), stop.nodeId());
			List<BusArrival> arrivals = TagoArrivalsJsonParser.parse(arrivalsJson, this.objectMapper);
			put(this.arrivalsCache, key, new Stamped<>(arrivals, now));
			return new StopArrivals(stop, arrivals);
		}
		catch (TransitVendorException exception) {
			if (cached != null && !isKeyRejected(exception)
					&& cached.youngerThan(now, this.properties.getArrivalsStaleTtl())) {
				return new StopArrivals(stop, cached.value());
			}
			throw exception;
		}
	}

	private static boolean isKeyRejected(TransitVendorException exception) {
		return "TRANSIT_VENDOR_NOT_CONFIGURED".equals(exception.getCode());
	}

	private static <T> void put(Map<String, Stamped<T>> cache, String key, Stamped<T> value) {
		if (cache.size() >= MAX_ENTRIES) {
			cache.entrySet().stream()
					.min((a, b) -> a.getValue().at().compareTo(b.getValue().at()))
					.ifPresent((oldest) -> cache.remove(oldest.getKey()));
		}
		cache.put(key, value);
	}

	private record Stamped<T>(T value, Instant at) {

		boolean youngerThan(Instant now, Duration ttl) {
			return Duration.between(this.at, now).compareTo(ttl) < 0;
		}
	}

	private static boolean hasBuses(StopArrivals stop) {
		return stop.arrivals() != null && !stop.arrivals().isEmpty();
	}

	private static long withBuses(List<StopArrivals> stops) {
		return stops.stream().filter(TransitService::hasBuses).count();
	}
}
