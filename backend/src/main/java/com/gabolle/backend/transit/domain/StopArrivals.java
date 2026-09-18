package com.gabolle.backend.transit.domain;

import java.util.List;

/** 정류소 하나 + 그 정류소에 도착 예정인 버스 목록 — S15P21E201-988. 버스가 없으면 빈 목록. */
public record StopArrivals(NearbyBusStop stop, List<BusArrival> arrivals) {
}
