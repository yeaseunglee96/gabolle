package com.gabolle.backend.place.service;

/**
 * 두 좌표 사이의 거리와, 반경을 감싸는 경계상자.
 *
 * <h2>왜 자바에서 재는가</h2>
 *
 * PostGIS 를 M1 에 켜지 않기로 했고({@code docs/DB-STANDARD.md} 4절), JPQL 에는 삼각함수가 없다.
 * 그렇다고 네이티브 쿼리를 쓰면 {@code gabolle} schema 를 못 찾는 함정에 빠진다 — 그리고 그 함정은
 * 테스트로 안 잡힌다(테스트는 표를 {@code public} 에 만든다). 그래서 {@link #boundingBox} 로 후보를
 * 좁히고 {@link #meters} 로 자바에서 정확한 거리를 잰다.
 *
 * <p>경계상자가 반경보다 넓은 사각형이라 바깥 후보가 섞여 들어오지만, 자바에서 실제 거리로 다시
 * 거르므로 결과는 정확하다. 넓은 만큼 읽는 행이 늘 뿐이다.
 *
 * <p>PostGIS 로 넘어갈 신호는 반경 질의가 눈에 띄게 느려지거나 장소가 10만 행을 넘을 때다.
 * 그때는 {@code geography(Point,4326)} 칼럼을 더하는 마이그레이션 하나로 이 클래스를 대체한다.
 */
public final class GeoDistance {

	/** 지구 평균 반지름(m). 하버사인이 구를 가정하므로 이 값 하나로 충분하다. */
	private static final double EARTH_RADIUS_METERS = 6_371_008.8;

	private GeoDistance() {
	}

	/**
	 * 하버사인 거리(m). 두 점이 가까울수록 정확하고, 부산 안의 거리에서는 오차가 무시할 수준이다.
	 */
	public static double meters(double lat1, double lng1, double lat2, double lng2) {
		double dLat = Math.toRadians(lat2 - lat1);
		double dLng = Math.toRadians(lng2 - lng1);
		double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
				+ Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
						* Math.sin(dLng / 2) * Math.sin(dLng / 2);
		return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1.0, Math.sqrt(a)));
	}

	/**
	 * 반경을 감싸는 경계상자. 질의를 좁히는 용도이므로 <b>넉넉한 쪽</b>으로 만든다 — 좁게 만들면
	 * 반경 안의 장소를 빠뜨린다.
	 *
	 * <p>🔴 경도 폭은 위도에 따라 달라진다({@code cos(lat)}). 위도 폭과 같게 두면 고위도에서
	 * 상자가 좁아져 결과가 빠진다. 극점 근처에서 {@code cos} 가 0에 가까워지는 것은 하한으로 막는다 —
	 * 부산에서는 걸릴 일이 없지만, 나중에 다른 지역이 들어와도 조용히 틀리지 않게 해 둔다.
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
	 * 경계상자.
	 *
	 * <p>경도가 ±180 을 넘어가는 경우(날짜변경선)는 여기서 다루지 않는다. 부산 서비스에서 생기지
	 * 않고, 처리한 척하면 검증 없는 코드가 남는다. 필요해지면 그때 상자를 둘로 쪼갠다.
	 */
	public record BoundingBox(double minLat, double maxLat, double minLng, double maxLng) {
	}
}
