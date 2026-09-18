package com.gabolle.backend.weather.domain;

/**
 * 기상청 단기예보 격자 좌표 하나 — S15P21E201-366.
 *
 * <p>기상청은 위경도가 아니라 5km 간격 격자 번호(nx, ny)로 예보를 준다.
 * {@link KmaGridConverter} 가 위경도에서 이 좌표를 만든다.
 */
public record KmaGridCoordinate(int nx, int ny) {
}
