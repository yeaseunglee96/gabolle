package com.gabolle.backend.transit.domain;

/** 어디 근처의 버스 도착정보를 볼 것인가 — WGS84 좌표. */
public record NearbyBusArrivalsQuery(double lat, double lng) {
}
