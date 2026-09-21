package com.gabolle.backend.transit.domain;

/**
 * TAGO {@code getSttnAcctoArvlPrearngeInfoList}(버스도착정보) 응답 한 행.
 * {@code arrivalSeconds}·{@code remainingStops}는 정보가 아예 없으면 {@code null} — 0으로 답하면
 * "곧 온다"와 "잴 수 없다"가 구분되지 않는다.
 */
public record BusArrival(String routeNo, Integer arrivalSeconds, Integer remainingStops, String vehicleType) {
}
