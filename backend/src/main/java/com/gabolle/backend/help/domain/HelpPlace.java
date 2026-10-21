package com.gabolle.backend.help.domain;

import java.time.DayOfWeek;
import java.util.Map;

/**
 * 가까운 도움 한 곳.
 *
 * @param type 종별 — 「상급종합」「종합병원」「병원」「의원」「보건소」「약국」「경찰」
 * @param nameEn 영어 이름. 경찰(OSM)에만 있을 때가 있다. 없으면 null — 앱이 로마자로 읽는다
 * @param hours 요일별 진료시간. 모르는 요일은 없다. 모르면 빈 표
 */
public record HelpPlace(
		HelpKind kind,
		String type,
		String name,
		String nameEn,
		String address,
		String phone,
		double lat,
		double lng,
		boolean emergency,
		Map<DayOfWeek, DayHours> hours) {

	/**
	 * 하루의 진료시간 — {@code 900}·{@code 1830} 처럼 시·분 네 자리. 쉬는 날이면 {@code closed}.
	 */
	public record DayHours(boolean closed, int open, int close) {

		public static DayHours closedDay() {
			return new DayHours(true, 0, 0);
		}
	}
}
