package com.gabolle.backend.weather.application;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.gabolle.backend.weather.domain.KmaGridConverter;
import com.gabolle.backend.weather.domain.KmaGridCoordinate;

/**
 * 위경도 범위를 기상청 격자 칸 목록으로 펼친다.
 *
 * 꼭짓점 넷을 변환해 사이를 채우지 않고 범위 안을 촘촘히 찍는다 — 격자는 위경도 격자가
 * 아니라 람베르트 원뿔 도법 위의 5km 격자라, 도법이 휘는 만큼 가장자리 칸이 빠진다.
 */
public final class BusanGridEnumerator {

	/** 훑는 간격(도). 부산 위도에서 약 2.2km 라 격자 5km 의 절반보다 촘촘해 건너뛰는 칸이 없다. */
	static final double STEP_DEG = 0.02;

	private BusanGridEnumerator() {
	}

	/** 범위를 덮는 격자 칸들. 중복은 없고 남서 → 북동 순서를 지킨다. */
	public static List<KmaGridCoordinate> enumerate(double latMin, double latMax, double lonMin, double lonMax) {
		if (latMin > latMax || lonMin > lonMax) {
			throw new IllegalArgumentException(
					"범위가 뒤집혔습니다: lat " + latMin + "~" + latMax + ", lon " + lonMin + "~" + lonMax);
		}
		Set<KmaGridCoordinate> grids = new LinkedHashSet<>();
		for (double lat = latMin; lat <= latMax + 1e-9; lat += STEP_DEG) {
			for (double lon = lonMin; lon <= lonMax + 1e-9; lon += STEP_DEG) {
				grids.add(KmaGridConverter.toGrid(lat, lon));
			}
		}
		// 간격으로 더해 가면 latMax·lonMax 에 정확히 안 닿아 북동쪽 끝 칸이 빠질 수 있다.
		grids.add(KmaGridConverter.toGrid(latMax, lonMax));
		grids.add(KmaGridConverter.toGrid(latMin, lonMax));
		grids.add(KmaGridConverter.toGrid(latMax, lonMin));
		return List.copyOf(grids);
	}

}
