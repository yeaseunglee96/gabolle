package com.gabolle.backend.route.domain;

import java.time.OffsetDateTime;

/**
 * 경로를 묻는 요청. 위·경도 범위 검사를 컨트롤러가 아니라 여기서 한다 — 일정 생성기가
 * 서비스를 직접 부를 때도 검사를 지나치지 않게.
 *
 * departureAt 은 대중교통에만 뜻이 있고 비워 둘 수 있다. 비어 있으면 탐색기가 "보통 이
 * 정도 걸린다" 로 답하고 estimated=true 를 싣는다.
 * 이 값은 캐시 열쇠의 일부라 초 단위로 다 다르면 캐시가 거의 안 맞는다 — 부르는 쪽이
 * 분 단위로 맞춰 넘기는 편이 낫다.
 */
public record RouteQuery(double originLat, double originLng, double destLat, double destLng, TravelMode mode,
		OffsetDateTime departureAt) {

	public RouteQuery {
		requireLat(originLat, "originLat");
		requireLng(originLng, "originLng");
		requireLat(destLat, "destLat");
		requireLng(destLng, "destLng");
		if (mode == null) {
			throw new IllegalArgumentException("이동수단이 필요합니다.");
		}
	}

	/** 출발 시각 없이 묻는다. 대중교통이면 "보통 이 정도" 로 답한다. */
	public RouteQuery(double originLat, double originLng, double destLat, double destLng, TravelMode mode) {
		this(originLat, originLng, destLat, destLng, mode, null);
	}

	/** 출발 시각을 알고 묻는가. 대중교통 탐색기가 시각표를 쓸지 말지를 이것으로 가른다. */
	public boolean hasDepartureTime() {
		return this.departureAt != null;
	}

	private static void requireLat(double value, String field) {
		if (Double.isNaN(value) || value < -90 || value > 90) {
			throw new IllegalArgumentException(field + " 위도 범위를 벗어났습니다: " + value);
		}
	}

	private static void requireLng(double value, String field) {
		if (Double.isNaN(value) || value < -180 || value > 180) {
			throw new IllegalArgumentException(field + " 경도 범위를 벗어났습니다: " + value);
		}
	}
}
