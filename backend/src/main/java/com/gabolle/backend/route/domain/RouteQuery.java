package com.gabolle.backend.route.domain;

import java.time.OffsetDateTime;

/**
 * 경로를 묻는 요청 — S15P21E201-184.
 *
 * <p>🔴 위·경도 범위 검사를 <b>여기서</b> 한다. 컨트롤러에만 두면 나중에 일정 생성기가
 * 이 서비스를 직접 부를 때 검사를 지나치게 된다 — {@code Trip} 도메인이 같은 이유로 자기
 * 생성자에서 좌표를 검사한다.
 *
 * <h2>🔴 출발 시각은 대중교통에만 뜻이 있다 — S15P21E201-1104</h2>
 *
 * 자동차·도보는 언제 떠나든 걸리는 시간이 거의 같지만, <b>대중교통은 언제 떠나는지가
 * 답을 바꾼다.</b> 같은 두 역 사이라도 08:00 에는 3분이고 23:50 에는 막차를 놓쳐 못 간다.
 *
 * <p>그래서 {@code departureAt} 을 더했다. <b>비워 둘 수 있다</b> — 다섯 인자 생성자가
 * 그대로 남아 있어 기존에 부르던 곳은 한 줄도 안 고쳤다. 비어 있으면 대중교통 탐색기가
 * "보통 이 정도 걸린다" 로 답하고 <b>그 사실을 응답에 싣는다</b>({@code estimated=true}).
 *
 * <p>🔴 <b>이 값은 캐시 열쇠의 일부가 된다.</b> 시각이 다르면 답도 다르므로 그래야 맞다.
 * 다만 시각이 초 단위로 다 다르면 캐시가 거의 안 맞는다 — 부르는 쪽이 분 단위로 맞춰
 * 넘기는 편이 낫다.
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

	/**
	 * 출발 시각 없이 묻는다 — 기존에 부르던 모양 그대로다.
	 *
	 * <p>대중교통이면 "보통 이 정도" 로 답하고, 나머지 수단은 원래와 완전히 같다.
	 */
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
