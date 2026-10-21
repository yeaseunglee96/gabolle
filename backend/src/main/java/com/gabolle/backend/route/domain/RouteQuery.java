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
 *
 * stepFree 는 「계단과 급경사를 피하는 길」을 달라는 뜻이다 — 휠체어·유모차를 쓰거나 계단을 피하겠다고 답한
 * 사람의 걷는 구간. 지금은 우리 보행 그래프({@code WalkGraph})만 이 값을 읽고, 자동차·대중교통 업체와 직선
 * 어림은 무시한다(돌아갈 길을 모른다). 캐시 열쇠에 들어간다 — 안 넣으면 계단 길 답이 휠체어 사용자에게 나간다.
 */
public record RouteQuery(double originLat, double originLng, double destLat, double destLng, TravelMode mode,
		OffsetDateTime departureAt, boolean stepFree) {

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
		this(originLat, originLng, destLat, destLng, mode, null, false);
	}

	/** 계단을 가리지 않는 보통 길을 묻는다 — stepFree 가 생기기 전의 모양 그대로다. */
	public RouteQuery(double originLat, double originLng, double destLat, double destLng, TravelMode mode,
			OffsetDateTime departureAt) {
		this(originLat, originLng, destLat, destLng, mode, departureAt, false);
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
