package com.gabolle.backend.help.presentation.dto;

import java.util.List;

import com.gabolle.backend.help.application.HelpPlaceService;
import com.gabolle.backend.help.domain.HelpKind;

/**
 * 가까운 도움 응답.
 *
 * @param source 출처 한 줄 — 화면 아래에 그대로 적는다(공공누리 출처표시 · ODbL)
 * @param basedOn 자료 기준일(「2026-06-30」)
 */
public record NearbyHelpResponse(HelpKind kind, List<Place> places, String source, String basedOn) {

	/**
	 * @param distanceMeters 직선거리(m, 반올림)
	 * @param todayOpen 오늘 여는 시각 「09:00」. 모르거나 쉬는 날이면 null
	 * @param openNow 지금 진료 중인가. 오늘 쉬면 false, 진료시간을 모르면 null
	 */
	public record Place(
			String name,
			String nameEn,
			String type,
			String address,
			String phone,
			double lat,
			double lng,
			int distanceMeters,
			boolean emergency,
			String todayOpen,
			String todayClose,
			Boolean openNow) {
	}

	public static NearbyHelpResponse from(HelpPlaceService.NearbyHelp result) {
		List<Place> places = result.places().stream().map(found -> new Place(
				found.place().name(),
				found.place().nameEn(),
				found.place().type(),
				found.place().address(),
				found.place().phone(),
				found.place().lat(),
				found.place().lng(),
				(int) Math.round(found.distanceMeters()),
				found.place().emergency(),
				clock(found.today().open()),
				clock(found.today().close()),
				found.today().openNow())).toList();
		return new NearbyHelpResponse(result.kind(), places, result.source(), result.basedOn());
	}

	/** 900 → 「09:00」, 1830 → 「18:30」 */
	static String clock(Integer hhmm) {
		if (hhmm == null) {
			return null;
		}
		return String.format("%02d:%02d", hhmm / 100, hhmm % 100);
	}
}
