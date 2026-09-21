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
 * 좌표 근처의 정류소와 각 정류소의 실시간 버스 도착정보를 조회한다.
 * 근처 정류소를 찾은 뒤 가까운 순으로 {@code properties.maxStops}개까지만 골라 정류소마다
 * 도착정보를 따로 조회한다 — 정류소 하나당 벤더 호출이 하나씩 더 나간다.
 * 실시간 정보라 캐시하지 않는다. 1분 전 도착 예정 시간은 그 자체로 틀린 값이다.
 * 정류소 하나의 도착정보 조회가 실패하면 전체 요청이 실패한다 — 일부만 빼고 답하면 "이
 * 근처엔 버스가 이것뿐" 이라고 오해하게 된다.
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
