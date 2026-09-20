package com.gabolle.backend.weather.domain;

/** 기상청이 예보를 주는 단위. 위경도가 아니라 5km 간격 격자 번호다. */
public record KmaGridCoordinate(int nx, int ny) {
}
