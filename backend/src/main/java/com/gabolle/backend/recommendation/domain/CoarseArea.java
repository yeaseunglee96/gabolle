package com.gabolle.backend.recommendation.domain;

import java.util.Locale;

/**
 * 좌표를 대략 1km 칸 하나로 뭉갠 번호. 추천 로그에는 정밀 좌표를 남기지 않으면서 "같은
 * 동네인가" 는 알아야 해서 값 자체를 되돌릴 수 없게 만든다.
 *
 * {@code "35.15:129.05"} 가 아니라 {@code "3515:12905"} 인 것은 좌표처럼 보이는 값은 다음
 * 사람이 좌표로 쓰기 때문이다. 소수점 두 자리에서 자르면 부산 위도에서 위도 0.01° ≈ 1.11km,
 * 경도 0.01° ≈ 0.91km 라 대략 1km 칸이 된다 — 더 굵으면 같은 동네라는 말이 의미를 잃고 더
 * 가늘면 옆 건물도 다른 칸이 된다. 이 굵기는 설정이 아니다.
 */
public final class CoarseArea {

	/** 위·경도를 자르는 소수점 자리수. 대략 1km 칸이 된다. */
	private static final double SCALE = 100.0;

	private CoarseArea() {
	}

	/**
	 * @return {@code "3515:12905"} 모양의 칸 번호. 좌표 중 하나라도 없으면 {@code null}
	 */
	public static String of(Double lat, Double lng) {
		if (lat == null || lng == null) {
			return null;
		}
		return Math.round(lat * SCALE) + ":" + Math.round(lng * SCALE);
	}

	/** 저장 칸이 {@code VARCHAR(32)} 다 — 넘칠 수 없다는 것을 여기서 한 번 못 박는다. */
	public static int maxLength() {
		return 32;
	}

	/** 로그·오류 메시지에 쓸 때. {@code null} 을 문자열 "null" 로 만들지 않는다. */
	public static String describe(String areaCode) {
		return (areaCode == null) ? "(모름)" : areaCode.toLowerCase(Locale.ROOT);
	}
}
