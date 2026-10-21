package com.gabolle.backend.routepost;

import java.util.List;
import java.util.UUID;

/**
 * 루트 포스트로 올릴 경로의 사본(-2027). 숙소는 이미 빠진 상태다 — {@link RouteSnapshotBuilder} 가 뺀다.
 *
 * @param dayIndex 0부터 센 하루. {@code null} 이면 일정 전체
 * @param path 지도에 그릴 선 — {@code [lat, lng]} 점들
 */
public record RouteSnapshot(UUID tripId, String itineraryId, Integer dayIndex, int dayCount, List<Stop> stops,
		List<double[]> path, Integer durationMin, Integer distanceM) {

	/** 들른 곳 하나. */
	public record Stop(int dayIndex, UUID placeId, String name, double lat, double lng, Integer stayMinutes) {
	}
}
