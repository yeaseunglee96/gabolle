package com.gabolle.backend.transit.domain;

/**
 * TAGO {@code getCrdntPrxmtSttnList}(좌표기반근접정류소목록조회) 응답 한 행.
 * {@code cityCode}는 도착정보 조회에서 {@code nodeId}와 짝으로 필요해 같이 들고 다닌다.
 */
public record NearbyBusStop(String nodeId, String nodeName, String cityCode, double lat, double lng) {
}
