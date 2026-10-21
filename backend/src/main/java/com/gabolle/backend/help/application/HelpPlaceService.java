package com.gabolle.backend.help.application;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.gabolle.backend.help.domain.HelpKind;
import com.gabolle.backend.help.domain.HelpPlace;
import com.gabolle.backend.place.service.GeoDistance;

/**
 * 내 위치에서 가까운 병원·약국·경찰(S15P21E201-1893). 긴급 도움의 「가까운 병원·약국·경찰 지도」가 부른다.
 *
 * <p>좌표만으로 답이 정해지는 조회라 사용자별로 다른 것이 없다. 거리는 직선(하버사인)이다 — 화면도 「직선 ○○m」라고 적는다.
 *
 * <p>「지금 진료 중」은 요일별 진료시간을 아는 곳에만 답한다(모르면 null). 🔴 공휴일은 모른다 — 자료의 공휴일 칸은
 * 「휴진」「오후 1시 이후」 같은 자유 글이라 셀 수 없다. 그래서 화면은 「가기 전에 전화로 확인하세요」를 함께 적는다.
 */
@Service
public class HelpPlaceService {

	/** 부산 밖이면 없다고 말한다 — 수십 km 밖의 곳은 「가까운 곳」이 아니다 */
	public static final int MAX_RADIUS_METERS = 5_000;

	public static final int MAX_LIMIT = 20;

	private static final ZoneId BUSAN = ZoneId.of("Asia/Seoul");

	private final HelpPlaceCatalog catalog;

	private final Clock clock;

	public HelpPlaceService(HelpPlaceCatalog catalog, Clock clock) {
		this.catalog = catalog;
		this.clock = clock;
	}

	public NearbyHelp nearby(HelpKind kind, double lat, double lng, int limit, boolean openNowOnly) {
		if (!Double.isFinite(lat) || !Double.isFinite(lng) || lat < -90 || lat > 90 || lng < -180 || lng > 180) {
			throw new IllegalArgumentException("좌표가 올바르지 않다");
		}
		int size = Math.max(1, Math.min(limit, MAX_LIMIT));
		ZonedDateTime now = ZonedDateTime.now(this.clock).withZoneSameInstant(BUSAN);
		List<Found> found = this.catalog.places(kind).stream()
				.map(place -> new Found(place, GeoDistance.meters(lat, lng, place.lat(), place.lng()), today(place, now)))
				.filter(item -> item.distanceMeters() <= MAX_RADIUS_METERS)
				.filter(item -> !openNowOnly || Boolean.TRUE.equals(item.today().openNow()))
				.sorted(Comparator.comparingDouble(Found::distanceMeters))
				.limit(size)
				.toList();
		return new NearbyHelp(kind, found, this.catalog.source(), this.catalog.basedOn());
	}

	/** 오늘의 진료시간과 지금 진료 중인가 — 모르면 둘 다 null */
	public static Today today(HelpPlace place, ZonedDateTime now) {
		HelpPlace.DayHours hours = place.hours().get(now.getDayOfWeek());
		if (hours == null) {
			return new Today(null, null, null);
		}
		if (hours.closed()) {
			return new Today(null, null, false);
		}
		int nowHhmm = now.getHour() * 100 + now.getMinute();
		return new Today(hours.open(), hours.close(), nowHhmm >= hours.open() && nowHhmm < hours.close());
	}

	public record NearbyHelp(HelpKind kind, List<Found> places, String source, String basedOn) {
	}

	public record Found(HelpPlace place, double distanceMeters, Today today) {
	}

	/**
	 * @param open 오늘 여는 시각(900 = 09:00). 모르거나 쉬는 날이면 null
	 * @param openNow 지금 진료 중인가. 오늘 쉬면 false, 모르면 null
	 */
	public record Today(Integer open, Integer close, Boolean openNow) {
	}
}
