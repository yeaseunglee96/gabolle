package com.gabolle.backend.route.domain;

/**
 * 경로를 묻는 요청 — S15P21E201-184.
 *
 * <p>🔴 위·경도 범위 검사를 <b>여기서</b> 한다. 컨트롤러에만 두면 나중에 일정 생성기가
 * 이 서비스를 직접 부를 때 검사를 지나치게 된다 — {@code Trip} 도메인이 같은 이유로 자기
 * 생성자에서 좌표를 검사한다.
 */
public record RouteQuery(double originLat, double originLng, double destLat, double destLng, TravelMode mode) {

	public RouteQuery {
		requireLat(originLat, "originLat");
		requireLng(originLng, "originLng");
		requireLat(destLat, "destLat");
		requireLng(destLng, "destLng");
		if (mode == null) {
			throw new IllegalArgumentException("이동수단이 필요합니다.");
		}
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
