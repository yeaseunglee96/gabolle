package com.gabolle.backend.recommendation.domain;

/**
 * 거리를 띠로 묶은 값. 미터는 {@code feature_values.distanceM} 에 따로 있고, 이 띠는 집계용
 * 이다 — 미터로 세면 질의마다 경계를 다시 정하게 되어 같은 지표가 사람마다 다른 숫자가 된다.
 *
 * 미터는 정밀 좌표가 아니지만 무해하지도 않다. 장소 좌표를 아는 상태에서 후보 셋의 정확한
 * 거리를 알면 출발지를 삼각측량으로 좁힐 수 있고, 띠만 남기면 그 계산이 무의미해진다.
 */
public enum DistanceBucket {

	/** 걸어서 몇 분. */
	UNDER_500M,

	/** 걷기 상한 안쪽. */
	M500_TO_1KM,

	/** 한 정류장~두 정류장. */
	KM1_TO_3KM,

	/** 대중교통이 필요한 거리. */
	KM3_TO_10KM,

	/** 하루 안에 왕복이 부담되는 거리. */
	OVER_10KM;

	/**
	 * @param distanceM 출발지에서 후보까지의 미터. 음수면 {@code null}({@code 0} 을 만들지
	 *     않는다 — 음수 거리는 "가까움" 이 아니라 계산이 잘못된 것이다)
	 */
	public static DistanceBucket of(Long distanceM) {
		if (distanceM == null || distanceM < 0) {
			return null;
		}
		if (distanceM < 500) {
			return UNDER_500M;
		}
		if (distanceM < 1_000) {
			return M500_TO_1KM;
		}
		if (distanceM < 3_000) {
			return KM1_TO_3KM;
		}
		if (distanceM < 10_000) {
			return KM3_TO_10KM;
		}
		return OVER_10KM;
	}
}
