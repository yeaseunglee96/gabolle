package com.gabolle.backend.transit.domain;

/**
 * 정류소 하나에 도착 예정인 버스 한 대 — S15P21E201-988.
 *
 * <p>TAGO {@code getSttnAcctoArvlPrearngeInfoList}(버스도착정보) 응답 한 행. {@code
 * arrivalSeconds}·{@code remainingStops}는 정보가 아예 없으면(예: 그 시간대 운행 종료)
 * {@code null} — 0으로 답하면 "곧 온다"와 "잴 수 없다"가 구분되지 않는다.
 */
public record BusArrival(String routeNo, Integer arrivalSeconds, Integer remainingStops, String vehicleType) {
}
