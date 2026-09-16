package com.gabolle.backend.transit.presentation.dto;

import java.util.List;

import com.gabolle.backend.transit.domain.NearbyBusArrivalsResult;

/** {@code GET /api/v1/transit/nearby-bus-arrivals} 응답 — S15P21E201-988. */
public record NearbyBusArrivalsResponseDto(List<StopDto> stops) {

	public static NearbyBusArrivalsResponseDto from(NearbyBusArrivalsResult result) {
		return new NearbyBusArrivalsResponseDto(result.stops().stream().map(StopDto::from).toList());
	}

	public record StopDto(String nodeId, String nodeName, double lat, double lng, List<ArrivalDto> arrivals) {

		static StopDto from(com.gabolle.backend.transit.domain.StopArrivals stopArrivals) {
			return new StopDto(
					stopArrivals.stop().nodeId(),
					stopArrivals.stop().nodeName(),
					stopArrivals.stop().lat(),
					stopArrivals.stop().lng(),
					stopArrivals.arrivals().stream().map(ArrivalDto::from).toList());
		}
	}

	/**
	 * @param arrivalSeconds 도착까지 남은 초. 정보가 없으면 {@code null}
	 * @param remainingStops 몇 정류장 전인지. 정보가 없으면 {@code null}
	 */
	public record ArrivalDto(String routeNo, Integer arrivalSeconds, Integer remainingStops, String vehicleType) {

		static ArrivalDto from(com.gabolle.backend.transit.domain.BusArrival arrival) {
			return new ArrivalDto(arrival.routeNo(), arrival.arrivalSeconds(), arrival.remainingStops(),
					arrival.vehicleType());
		}
	}
}
