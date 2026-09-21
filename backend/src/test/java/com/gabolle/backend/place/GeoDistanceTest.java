package com.gabolle.backend.place;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.place.service.GeoDistance;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 거리 계산과 경계상자. 거리 정렬이 이 함수 하나에 걸려 있는데 틀려도 결과가 그럴듯해
 * 보이므로 알려진 값으로 못 박는다. DB 없이 돈다.
 */
class GeoDistanceTest {

	// 부산역과 해운대해수욕장. 실제 직선거리는 약 12.5km 다.
	private static final double BUSAN_STATION_LAT = 35.1151;
	private static final double BUSAN_STATION_LNG = 129.0413;
	private static final double HAEUNDAE_LAT = 35.1587;
	private static final double HAEUNDAE_LNG = 129.1604;

	@Test
	@DisplayName("같은 점 사이의 거리는 0이다")
	void distanceToSelfIsZero() {
		double meters = GeoDistance.meters(BUSAN_STATION_LAT, BUSAN_STATION_LNG,
				BUSAN_STATION_LAT, BUSAN_STATION_LNG);

		assertThat(meters).isZero();
	}

	@Test
	@DisplayName("부산역에서 해운대까지 약 12.5km — 알려진 값에서 크게 벗어나지 않는다")
	void distanceBetweenKnownPointsMatches() {
		double meters = GeoDistance.meters(BUSAN_STATION_LAT, BUSAN_STATION_LNG, HAEUNDAE_LAT, HAEUNDAE_LNG);

		assertThat(meters).isBetween(11_500.0, 13_500.0);
	}

	@Test
	@DisplayName("거리는 방향에 상관없이 같다")
	void distanceIsSymmetric() {
		double forward = GeoDistance.meters(BUSAN_STATION_LAT, BUSAN_STATION_LNG, HAEUNDAE_LAT, HAEUNDAE_LNG);
		double backward = GeoDistance.meters(HAEUNDAE_LAT, HAEUNDAE_LNG, BUSAN_STATION_LAT, BUSAN_STATION_LNG);

		assertThat(forward).isCloseTo(backward, org.assertj.core.data.Offset.offset(0.001));
	}

	@Test
	@DisplayName("🔴 경계상자는 반경 안의 점을 하나도 빠뜨리지 않는다 — 좁게 만들면 결과가 조용히 사라진다")
	void boundingBoxContainsEveryPointWithinRadius() {
		double radius = 3_000.0;
		GeoDistance.BoundingBox box = GeoDistance.boundingBox(BUSAN_STATION_LAT, BUSAN_STATION_LNG, radius);

		// 동서남북 네 방향으로 반경만큼 떨어진 점이 전부 상자 안에 있어야 한다.
		double latDelta = Math.toDegrees(radius / 6_371_008.8);
		double lngDelta = Math.toDegrees(radius / (6_371_008.8 * Math.cos(Math.toRadians(BUSAN_STATION_LAT))));

		assertThat(BUSAN_STATION_LAT + latDelta).isLessThanOrEqualTo(box.maxLat());
		assertThat(BUSAN_STATION_LAT - latDelta).isGreaterThanOrEqualTo(box.minLat());
		assertThat(BUSAN_STATION_LNG + lngDelta).isLessThanOrEqualTo(box.maxLng());
		assertThat(BUSAN_STATION_LNG - lngDelta).isGreaterThanOrEqualTo(box.minLng());
	}

	@Test
	@DisplayName("🔴 경도 폭이 위도 폭보다 넓다 — 같게 두면 고위도에서 결과가 빠진다")
	void longitudeSpanIsWiderThanLatitudeSpan() {
		GeoDistance.BoundingBox box = GeoDistance.boundingBox(BUSAN_STATION_LAT, BUSAN_STATION_LNG, 3_000.0);

		double latSpan = box.maxLat() - box.minLat();
		double lngSpan = box.maxLng() - box.minLng();

		assertThat(lngSpan).isGreaterThan(latSpan);
	}

	@Test
	@DisplayName("위도는 ±90을 넘지 않는다")
	void latitudeIsClamped() {
		GeoDistance.BoundingBox box = GeoDistance.boundingBox(89.99, 0.0, 100_000.0);

		assertThat(box.maxLat()).isLessThanOrEqualTo(90.0);
		assertThat(box.minLat()).isGreaterThanOrEqualTo(-90.0);
	}
}
