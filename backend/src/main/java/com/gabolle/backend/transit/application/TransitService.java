package com.gabolle.backend.transit.application;

import java.util.List;

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
 * 좌표 근처의 정류소와 각 정류소의 실시간 버스 도착정보를 조회한다 — S15P21E201-988.
 *
 * <h2>순서</h2>
 * <ol>
 *   <li>좌표로 근처 정류소를 찾는다({@code getCrdntPrxmtSttnList})</li>
 *   <li>가까운 순으로 {@code properties.maxStops}개까지만 골라, 정류소마다 도착정보를
 *       따로 조회한다({@code getSttnAcctoArvlPrearngeInfoList}) — 정류소 하나당 벤더 호출이
 *       하나씩 더 나가므로 개수를 제한한다</li>
 * </ol>
 *
 * <p>🔴 <b>실시간 정보라 캐시하지 않는다.</b> {@code WeatherService}와 정반대 결정이다 —
 * 날씨는 발표 회차 안에서 값이 안 바뀌지만, 버스 도착 예정 시간은 1분 전 값을 보여주면
 * 그 자체로 틀린 정보가 된다.
 *
 * <p>🔴 <b>정류소 하나의 도착정보 조회가 실패하면 전체 요청이 실패한다.</b> 일부만 조용히
 * 빼고 나머지로 답하면, 사용자는 "이 근처엔 버스가 이것뿐" 이라고 오해할 수 있다 —
 * {@code WeatherService}가 벤더 실패를 숨기지 않는 것과 같은 이유.
 */
@Service
@Profile({ "db", "dev" })
public class TransitService {

	private final TransitVendorPort vendor;

	private final TransitProperties properties;

	private final ObjectMapper objectMapper;

	public TransitService(TransitVendorPort vendor, TransitProperties properties, ObjectMapper objectMapper) {
		this.vendor = vendor;
		this.properties = properties;
		this.objectMapper = objectMapper;
	}

	public NearbyBusArrivalsResult nearbyArrivals(NearbyBusArrivalsQuery query) {
		String stopsJson = this.vendor.fetchNearbyStopsJson(query.lat(), query.lng());
		List<NearbyBusStop> nearbyStops = TagoNearbyStopsJsonParser.parse(stopsJson, this.objectMapper);

		int limit = Math.min(nearbyStops.size(), this.properties.getMaxStops());
		List<StopArrivals> stops = nearbyStops.subList(0, limit).stream()
				.map(this::withArrivals)
				.toList();

		return new NearbyBusArrivalsResult(stops);
	}

	private StopArrivals withArrivals(NearbyBusStop stop) {
		String arrivalsJson = this.vendor.fetchArrivalsJson(stop.cityCode(), stop.nodeId());
		List<BusArrival> arrivals = TagoArrivalsJsonParser.parse(arrivalsJson, this.objectMapper);
		return new StopArrivals(stop, arrivals);
	}
}
