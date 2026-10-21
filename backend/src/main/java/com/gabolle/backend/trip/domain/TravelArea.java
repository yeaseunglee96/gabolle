package com.gabolle.backend.trip.domain;

import java.util.Optional;

/**
 * 여행 범위로 고를 수 있는 부산의 지역. 코드는 앱의 {@code AREAS} 와 같다.
 *
 * <p>중심과 반경을 표가 아니라 여기에 둔다. 표에 적으면 지역 중심을 옮길 때 이미 만들어진
 * 여행만 옛 좌표로 남아, 같은 "해운대" 가 여행마다 다른 곳을 뜻하게 된다. 반경은 그 동네를
 * 걸어 다니는 범위다 — 좁히면 후보가 모자라고 넓히면 옆 동네가 섞인다.
 */
public enum TravelArea {

	HAEUNDAE("해운대", 35.1587, 129.1604, 2_500, 35.163672, 129.158908),   // 해운대역 2호선
	GWANGALLI("광안리", 35.1532, 129.1186, 2_000, 35.157916, 129.113168),  // 광안역 2호선
	NAMPO("남포동", 35.0980, 129.0306, 2_000, 35.097953, 129.034869),      // 남포역 1호선
	SEOMYEON("서면", 35.1578, 129.0594, 2_000, 35.158282, 129.059556),     // 서면역 1호선
	YEONGDO("영도", 35.0911, 129.0682, 3_000, null, null),
	SONGJEONG("송정", 35.1786, 129.1996, 2_000, null, null);

	private final String koreanName;

	private final double lat;

	private final double lng;

	private final int radiusM;

	/**
	 * 이 동네를 숙소로 고른 여행의 기준점 — 그 동네의 지하철역 (S15P21E201-2046).
	 * 중심은 길이 닿는 자리가 아니라서 앱이 숙소↔장소 구간을 곧은 점선으로 그렸다. 역은 실제 길이 시작하는 곳이다.
	 * 좌표는 부산교통공사 역 자료({@code transit/busan-subway-network.json}) 값 그대로다. 정하지 않은 동네는 중심을 쓴다.
	 */
	private final Double stationLat;

	private final Double stationLng;

	TravelArea(String koreanName, double lat, double lng, int radiusM, Double stationLat, Double stationLng) {
		this.stationLat = stationLat;
		this.stationLng = stationLng;
		this.koreanName = koreanName;
		this.lat = lat;
		this.lng = lng;
		this.radiusM = radiusM;
	}

	/**
	 * 모르는 코드는 예외가 아니라 빈 값으로 답한다. 앱이 새 지역을 먼저 내보내는 날 여행 생성이
	 * 400 으로 막히면 안 되므로, 모르는 지역은 "범위를 안 골랐다" 와 같게 다룬다.
	 */
	public static Optional<TravelArea> of(String code) {
		if (code == null || code.isBlank()) {
			return Optional.empty();
		}
		for (TravelArea area : values()) {
			if (area.name().equalsIgnoreCase(code.strip())) {
				return Optional.of(area);
			}
		}
		return Optional.empty();
	}

	public String koreanName() {
		return this.koreanName;
	}

	/** 숙소 기준점 위도 — 역이 있으면 역, 없으면 중심. */
	public double lodgingLat() {
		return this.stationLat != null ? this.stationLat : this.lat;
	}

	/** 숙소 기준점 경도 — 역이 있으면 역, 없으면 중심. */
	public double lodgingLng() {
		return this.stationLng != null ? this.stationLng : this.lng;
	}

	public double lat() {
		return this.lat;
	}

	public double lng() {
		return this.lng;
	}

	public int radiusM() {
		return this.radiusM;
	}
}
