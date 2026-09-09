package com.gabolle.backend.recommendation.domain;

import java.time.Instant;

/**
 * 이 요청 하나에만 쓰이는 위치 (S15P21E201-550).
 *
 * <p>FR-ACC-05 · FR-REC-10 · S-12. 현재 위치 기반 추천을 제공하면서도 정확한 좌표를
 * 영구 행동 이력으로 남기지 않는다.
 *
 * <h2>🔴 이 객체는 저장되지 않는다</h2>
 *
 * <p>여기 담긴 좌표는 <b>후보 거리·이동시간·영업 가능성 계산에만</b> 쓰이고 요청이 끝나면
 * 사라진다. 남는 것은 파생값뿐이다.
 *
 * <table border="1">
 * <caption>무엇이 남고 무엇이 안 남나</caption>
 * <tr><th></th><th>어디에</th></tr>
 * <tr><td>{@code lat}·{@code lng} (정밀 좌표)</td>
 *     <td>🔴 <b>아무 데도 안 남는다.</b> 칸도 없다</td></tr>
 * <tr><td>{@link #areaCode()} (대략 1km 칸)</td>
 *     <td>{@code recommendation_job.origin_area_code}</td></tr>
 * <tr><td>{@link #source()}</td>
 *     <td>{@code recommendation_job.origin_source}</td></tr>
 * <tr><td>후보별 거리·거리 띠</td>
 *     <td>{@code recommendation_candidate.feature_values}</td></tr>
 * </table>
 *
 * <p>그래서 <b>정확 좌표 보존 기간이 0 이고, 그것을 검사할 수 있다</b>(-550 완료 기준 3) —
 * 지울 것을 관리하는 대신 애초에 넣을 자리를 안 만들었다. 검사는
 * {@code RequestLocationPrivacyTest} 가 한다.
 *
 * <h2>🔴 {@code toString} 이 좌표를 찍지 않는다</h2>
 *
 * <p>record 의 기본 {@code toString} 은 모든 칸을 그대로 찍는다. 그러면 예외 메시지나
 * 디버그 로그 한 줄에 좌표가 실려 나가고, <b>그 경로는 {@code SensitivePayloadGuard} 가
 * 못 잡는다</b> — 그 그물은 저장되는 JSONB 만 본다. 실수로 새는 가장 흔한 길이 로그라서
 * 여기서 막는다.
 *
 * @param lat 위도. 저장되지 않는다
 * @param lng 경도. 저장되지 않는다
 * @param source 이 좌표가 어디서 왔나
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
		 * 사용자가 지도에서 찍거나 검색해서 고른 위치.
		 *
		 * <p>🔴 <b>이것은 열등한 경로가 아니다.</b> -550 의 작업 내용이 "위치 거부 시 수기
		 * 입력을 <b>1급 fallback</b> 으로 제공한다" 라고 못 박았다. 위치 권한을 거부한
		 * 사용자도 추천을 받아야 하고, 받은 추천의 품질이 다를 이유가 없다 — 거리 계산에
		 * 들어가는 값은 어느 쪽이든 좌표 하나다.
		 */
		MANUAL,

		/**
		 * 여행에 저장된 출발지({@code trip.origin_lat/lng})를 쓴 것.
		 *
		 * <p>요청이 위치를 아예 안 준 경우다 — 일정 생성처럼 "지금 어디 있는가" 가 무의미한
		 * 요청이 여기 해당한다.
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
		// 🔴 accuracyM 이 없다고 GPS 를 MANUAL 로 바꾸지 않는다. 출처는 부르는 쪽이 아는
		//    사실이고, 여기서 값 유무로 짐작하면 "오차를 안 보낸 GPS" 가 수기 입력으로
		//    집계된다.
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

	/**
	 * 🔴 좌표를 찍지 않는다 — 위 javadoc 참고. 출처와 칸만 남긴다.
	 */
	@Override
	public String toString() {
		return "RequestLocation[source=" + this.source + ", area=" + CoarseArea.describe(areaCode())
				+ ", accuracyM=" + this.accuracyM + "]";
	}
}
