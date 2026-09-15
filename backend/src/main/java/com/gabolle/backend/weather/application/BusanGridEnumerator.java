package com.gabolle.backend.weather.application;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.gabolle.backend.weather.domain.KmaGridConverter;
import com.gabolle.backend.weather.domain.KmaGridCoordinate;

/**
 * 위경도 범위를 기상청 격자 칸 목록으로 펼친다 — S15P21E201-993.
 *
 * <p>DB 도 네트워크도 안 본다. 순수 계산이라 컨테이너 없이 테스트한다.
 *
 * <h2>왜 꼭짓점 넷이 아니라 촘촘히 훑나</h2>
 *
 * 격자는 위경도 격자가 아니라 <b>람베르트 원뿔 도법</b> 위의 5km 격자다. 그래서 위경도
 * 사각형의 네 꼭짓점만 변환해 그 사이를 채우면, 도법이 휘는 만큼 <b>가장자리 칸이 빠진다.</b>
 * 범위 안을 일정 간격으로 찍어 나온 칸을 모으는 쪽이 코드도 짧고 빠뜨림도 없다.
 *
 * <p>간격은 {@value #STEP_DEG} 도다 — 부산 위도에서 위도 0.01° ≈ 1.11km 이므로 약 2.2km 이고,
 * 격자 간격(5km)의 절반보다 촘촘해서 건너뛰는 칸이 생기지 않는다.
 */
public final class BusanGridEnumerator {

	/** 훑는 간격(도). 격자 5km 의 절반보다 촘촘하다. */
	static final double STEP_DEG = 0.02;

	private BusanGridEnumerator() {
	}

	/**
	 * @return 범위를 덮는 격자 칸들. 중복은 없고, 넣은 순서(남서 → 북동)를 지킨다
	 */
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
		// 🔴 마지막 가장자리를 반드시 넣는다. 간격으로 더해 가면 latMax·lonMax 에 정확히 안 닿는
		//    경우가 대부분이라, 북동쪽 끝 칸이 빠질 수 있다.
		grids.add(KmaGridConverter.toGrid(latMax, lonMax));
		grids.add(KmaGridConverter.toGrid(latMin, lonMax));
		grids.add(KmaGridConverter.toGrid(latMax, lonMin));
		return List.copyOf(grids);
	}

}
