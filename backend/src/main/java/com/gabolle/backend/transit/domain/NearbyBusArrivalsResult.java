package com.gabolle.backend.transit.domain;

import java.util.List;

/** 근처 정류소들과 각 정류소의 도착 예정 버스 — S15P21E201-988. 근처에 정류소가 없으면 빈 목록. */
public record NearbyBusArrivalsResult(List<StopArrivals> stops) {
}
