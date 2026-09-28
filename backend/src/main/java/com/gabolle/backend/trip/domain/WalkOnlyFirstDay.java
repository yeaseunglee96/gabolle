package com.gabolle.backend.trip.domain;

import java.util.Arrays;

/**
 * 「걷기만」 고른 여행의 첫날은 출발지에서 걸어갈 만한 곳으로 — S15P21E201-1634(사용자 결정 2026-09-25).
 *
 * <p>🔴 왜. 걷기만 고른 여행(출발 부산역, 여행 범위 해운대)의 첫 구간이 15km 걷기(225분)였다. 후보를 여행 범위에서만
 * 뽑아 200곳이 전부 출발지에서 8~16km 였다. 추천 엔진은 출발지 둘레를 한 번 더 훑어 후보에 넣고, 일정 조립은 첫날에
 * 이 둘레 안의 곳만 앉힌다 — 두 자리가 같은 기준을 쓰게 여기 둔다.
 *
 * <p>기준은 걷는 시간 {@value #MINUTES}분이다. 걷기 속도 4km/h 와 길이 돌아가는 비율 1.3(둘 다 길찾기 설정
 * {@code gabolle.route} 의 기본값)으로 직선 반경 {@link #RADIUS_M}m 로 바꾼다. 부산역 둘레 30분 안에 밥집 121 ·
 * 카페 29 · 명소 30곳 남짓이 있다(2026-09-25 운영) — 하루(4~5곳)를 채우기에 넉넉하다.
 */
public final class WalkOnlyFirstDay {

	/** 첫날 곳들이 출발지에서 걸어서 이 시간 안이어야 한다(분). */
	public static final int MINUTES = 30;

	/** {@link #MINUTES} 를 직선 반경으로 바꾼 것(m) — 30분 × 4km/h ÷ 1.3 ≈ 1,538m. */
	public static final int RADIUS_M = (int) Math.round(MINUTES * 4000.0 / 60 / 1.3);

	/**
	 * 고른 여행 범위 밖인데 출발지 둘레라서 후보에 들어온 곳의 이유 코드 — 일정 조립은 이런 곳을 <b>첫날에만</b>
	 * 앉힌다. 출발지에서 가까워 점수가 높게 나오므로, 안 막으면 다른 날까지 출발지 쪽으로 끌려가 사용자가 고른
	 * 여행 범위를 거스른다.
	 */
	public static final String REASON_CODE = "WALK_ONLY_FIRST_DAY";

	private WalkOnlyFirstDay() {
	}

	/** 걷기 하나만 골랐고 출발지 좌표가 있는 여행인가. */
	public static boolean applies(Trip trip) {
		if (trip == null || trip.originLat() == null || trip.originLng() == null) {
			return false;
		}
		String[] modes = trip.travelModes();
		return modes != null && modes.length > 0 && Arrays.stream(modes).allMatch("WALK"::equals);
	}
}
