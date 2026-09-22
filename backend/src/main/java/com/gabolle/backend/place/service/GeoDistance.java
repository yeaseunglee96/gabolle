package com.gabolle.backend.place.service;

/**
 * 두 좌표 사이의 거리와, 반경을 감싸는 경계상자.
 *
 * <p>PostGIS 를 쓰지 않기로 했고({@code docs/DB-STANDARD.md} 4절) JPQL 에 삼각함수가 없어
 * 거리를 자바에서 잰다. {@link #boundingBox} 로 후보를 좁히고 {@link #meters} 로 거른다 —
 * 상자가 반경보다 넓어 바깥 후보가 섞여 들어오지만 실제 거리로 다시 거르므로 결과는 정확하다.
 *
 * <p>PostGIS 로 넘어갈 신호는 반경 질의가 느려지거나 장소가 10만 행을 넘을 때다.
 */
public final class GeoDistance {

	/** 지구 평균 반지름(m). 하버사인이 구를 가정하므로 이 값 하나로 충분하다. */
	private static final double EARTH_RADIUS_METERS = 6_371_008.8;

	private GeoDistance() {
	}

	/** 하버사인 거리(m). 구를 가정하므로 두 점이 멀수록 오차가 커진다. */
	public static double meters(double lat1, double lng1, double lat2, double lng2) {
		double dLat = Math.toRadians(lat2 - lat1);
		double dLng = Math.toRadians(lng2 - lng1);
		double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
				+ Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
						* Math.sin(dLng / 2) * Math.sin(dLng / 2);
		return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1.0, Math.sqrt(a)));
	}

	/**
	 * 반경을 감싸는 경계상자. 질의를 좁히는 용도라 넉넉한 쪽으로 만든다 — 좁히면 반경 안의
	 * 장소를 빠뜨린다.
	 *
	 * <p>경도 폭은 위도에 따라 달라지므로({@code cos(lat)}) 위도 폭과 같게 두면 고위도에서
	 * 결과가 빠진다. {@code cos} 하한은 극점 근처에서 0 으로 나누는 것을 막는다.
	 */
	public static BoundingBox boundingBox(double lat, double lng, double radiusMeters) {
		double latDelta = Math.toDegrees(radiusMeters / EARTH_RADIUS_METERS);
		double cosLat = Math.max(Math.cos(Math.toRadians(lat)), 1e-6);
		double lngDelta = Math.toDegrees(radiusMeters / (EARTH_RADIUS_METERS * cosLat));
		return new BoundingBox(
				clampLatitude(lat - latDelta), clampLatitude(lat + latDelta),
				lng - lngDelta, lng + lngDelta);
	}

	private static double clampLatitude(double value) {
		return Math.max(-90.0, Math.min(90.0, value));
	}

	/**
	 * 경도가 ±180 을 넘어가는 경우(날짜변경선)는 다루지 않는다. 필요해지면 상자를 둘로 쪼갠다.
	 */
	public record BoundingBox(double minLat, double maxLat, double minLng, double maxLng) {
	}
}
