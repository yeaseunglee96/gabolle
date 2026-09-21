package com.gabolle.backend.recommendation.domain;

import java.time.Instant;

/**
 * 이 요청 하나에만 쓰이는 위치. 현재 위치 기반 추천을 제공하면서도 정확한 좌표를 영구
 * 행동 이력으로 남기지 않는다.
 *
 * 이 객체는 저장되지 않는다. 담긴 좌표는 후보 거리·이동시간·영업 가능성 계산에만 쓰이고
 * 요청이 끝나면 사라지며, 남는 것은 파생값뿐이다 — {@link #areaCode()} 와 {@link #source()},
 * 후보별 거리·거리 띠. 정밀 좌표는 담을 칸 자체가 없다.
 *
 * {@code toString} 이 좌표를 찍지 않는다. record 의 기본 구현은 모든 칸을 찍어 예외 메시지나
 * 디버그 로그에 좌표가 실려 나가는데, {@code SensitivePayloadGuard} 는 저장되는 JSONB 만
 * 보므로 그 경로를 못 잡는다.
 *
 * @param accuracyM GPS 가 보고한 오차 반경(m). 수기 입력이면 {@code null}
 * @param capturedAt 좌표를 잡은 시각. 오래된 좌표를 그대로 쓰지 않기 위한 값이다
 */
public record RequestLocation(
		double lat,
		double lng,
		LocationSource source,
		Double accuracyM,
		Instant capturedAt) {

	/** 이 좌표가 어디서 왔나. */
	public enum LocationSource {

		/** 기기가 준 현재 위치. {@code accuracyM} 이 함께 온다. */
		GPS,

		/**
		 * 사용자가 지도에서 찍거나 검색해서 고른 위치. GPS 보다 열등한 경로가 아니다 —
		 * 거리 계산에 들어가는 값은 어느 쪽이든 좌표 하나다.
		 */
		MANUAL,

		/**
		 * 여행에 저장된 출발지({@code trip.origin_lat/lng})를 쓴 것. 요청이 위치를 아예 안 준
		 * 경우다 — 일정 생성처럼 "지금 어디 있는가" 가 무의미한 요청이 여기 해당한다.
		 */
		TRIP_ORIGIN
	}

	public RequestLocation {
		if (source == null) {
			throw new IllegalArgumentException("위치 출처(GPS/MANUAL/TRIP_ORIGIN)는 필수다");
		}
		if (lat < -90 || lat > 90) {
			throw new IllegalArgumentException("위도가 범위를 벗어났다: " + lat);
		}
		if (lng < -180 || lng > 180) {
			throw new IllegalArgumentException("경도가 범위를 벗어났다: " + lng);
		}
		if (accuracyM != null && accuracyM < 0) {
			throw new IllegalArgumentException("오차 반경은 0 이상이어야 한다: " + accuracyM);
		}
		// accuracyM 이 없다고 GPS 를 MANUAL 로 바꾸지 않는다. 출처는 부르는 쪽이 아는
		// 사실이고, 값 유무로 짐작하면 오차를 안 보낸 GPS 가 수기 입력으로 집계된다.
	}

	/** 여행 출발지에서 만든다 — 위치를 안 준 요청의 기본 경로. */
	public static RequestLocation ofTripOrigin(Double lat, Double lng, Instant at) {
		if (lat == null || lng == null) {
			return null;
		}
		return new RequestLocation(lat, lng, LocationSource.TRIP_ORIGIN, null, at);
	}

	/** 대략 1km 칸. 이것이 저장되는 값이다. */
	public String areaCode() {
		return CoarseArea.of(this.lat, this.lng);
	}

	/** 좌표를 찍지 않고 출처와 칸만 남긴다. */
	@Override
	public String toString() {
		return "RequestLocation[source=" + this.source + ", area=" + CoarseArea.describe(areaCode())
				+ ", accuracyM=" + this.accuracyM + "]";
	}
}
