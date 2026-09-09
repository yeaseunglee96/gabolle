package com.gabolle.backend.recommendation.domain;

import java.util.Locale;

/**
 * 좌표를 <b>대략 1km 칸</b> 하나로 뭉갠 번호 (S15P21E201-550).
 *
 * <h2>🔴 왜 굵게 만드는가</h2>
 *
 * <p>추천 로그(일반 이벤트·후보 행의 JSONB)에는 <b>정밀 좌표를 남기지 않는다</b>.
 * {@code SensitivePayloadGuard} 가 저장 직전에 그것을 거부하고, 그 거부가 맞다 —
 * 사용자의 현재 위치는 한 번 로그에 들어가면 지우는 비용이 다르다.
 *
 * <p>그런데 <b>"같은 동네인가" 는 알아야 한다.</b> 다양성 재정렬(S15P21E201-548)이
 * 한 동네가 상위를 독점하는 것을 막을 때, 그리고 분석이 "어느 지역에서 온 요청인가" 를
 * 셀 때 필요하다. 그 둘 다 <b>칸 단위면 충분하다</b> — 정밀 좌표가 필요한 계산은 거리
 * 하나이고, 그것은 요청 안에서 끝난다.
 *
 * <p>그래서 이름만 바꿔 그물을 피하지 않고 <b>값 자체를 되돌릴 수 없게</b> 만든다.
 *
 * <h2>🔴 소수점을 남기지 않는 것도 일부러다</h2>
 *
 * <p>{@code "35.15:129.05"} 가 아니라 {@code "3515:12905"} 다. 소수점이 있으면 좌표처럼
 * 보이고, <b>좌표처럼 보이는 값은 다음 사람이 좌표로 쓴다.</b> 정수 쌍은 그렇게 안 읽힌다.
 * (부수적으로 {@code SensitivePayloadGuard} 의 좌표 쌍 정규식에도 걸리지 않는다 — 그건
 * 결과일 뿐이고, 이유는 위의 것이다.)
 *
 * <h2>얼마나 굵은가</h2>
 *
 * <p>소수점 두 자리에서 자른다. 부산 위도(35.1°)에서 위도 0.01° ≈ 1.11km,
 * 경도 0.01° ≈ 0.91km — 즉 대략 1km 칸이다. 이보다 굵게 잡으면 부산 전체가 몇 칸이 되어
 * "같은 동네" 가 의미를 잃고, 가늘게 잡으면 옆 건물도 다른 칸이 되어 아무것도 묶이지 않는다.
 *
 * <p>🔴 <b>이 굵기는 설정이 아니다.</b> 정밀 좌표를 안 남기기 위한 값이라 설정으로
 * 되돌릴 수 있으면 안 된다.
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
