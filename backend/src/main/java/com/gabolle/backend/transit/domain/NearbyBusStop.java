package com.gabolle.backend.transit.domain;

/**
 * 좌표 근처의 버스 정류소 하나 — S15P21E201-988.
 *
 * <p>TAGO {@code getCrdntPrxmtSttnList}(좌표기반근접정류소목록조회) 응답 한 행을 그대로 옮긴
 * 것이다. {@code cityCode}는 도착정보 조회({@code nodeId}와 짝지어야만 뜻이 있다)에 그대로
 * 필요해서 여기 같이 들고 다닌다.
 */
public record NearbyBusStop(String nodeId, String nodeName, String cityCode, double lat, double lng) {
}
