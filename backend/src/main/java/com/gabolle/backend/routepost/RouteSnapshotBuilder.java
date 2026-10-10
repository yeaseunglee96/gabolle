package com.gabolle.backend.routepost;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.gabolle.backend.itinerary.presentation.dto.ItineraryDetailResponse;

/**
 * 일정 상세를 루트 포스트 사본으로 바꾼다(-2027). 상태가 없는 순수 계산이라 DB 없이 시험한다.
 *
 * <p>🔴 <b>숙소를 뺀다.</b> 숙소 정류장, 그 정류장으로 들어가는 이동선, 그 정류장에서 나오는 이동선,
 * 그리고 하루의 첫 이동선(출발점 = 보통 숙소에서 나오는 길)과 돌아가는 길은 넣지 않는다. 이동선의
 * 끝점이 곧 숙소의 정확한 위치라, 정류장만 빼고 선을 남기면 숙소가 선 끝에 그대로 드러난다.
 */
public final class RouteSnapshotBuilder {

	/** 지도 썸네일에 충분한 점 수. 이보다 많으면 고르게 솎는다 — 피드 한 쪽에 글이 20개다. */
	static final int MAX_PATH_POINTS = 400;

	private RouteSnapshotBuilder() {
	}

	/**
	 * @param lodgingPlaceIds 숙소인 장소 id
	 * @param dayIndex {@code null} 이면 전체
	 * @throws RoutePostExceptions.InvalidRouteException 그날이 없거나, 숙소를 빼고 남은 곳이 둘보다 적을 때
	 */
	public static RouteSnapshot build(ItineraryDetailResponse detail, Set<UUID> lodgingPlaceIds, Integer dayIndex) {
		List<ItineraryDetailResponse.Day> days = detail.days() == null ? List.of() : detail.days();
		if (dayIndex != null && (dayIndex < 0 || dayIndex >= days.size())) {
			throw new RoutePostExceptions.InvalidRouteException("DAY_NOT_FOUND");
		}
		List<RouteSnapshot.Stop> stops = new ArrayList<>();
		List<double[]> path = new ArrayList<>();
		int travelMin = 0;
		int stayMin = 0;
		boolean anyTime = false;
		for (int d = 0; d < days.size(); d++) {
			if (dayIndex != null && d != dayIndex) {
				continue;
			}
			List<ItineraryDetailResponse.Item> items = days.get(d).items() == null ? List.of() : days.get(d).items();
			boolean previousWasLodging = true; // 하루의 첫 이동선은 출발점(숙소)에서 온다
			for (ItineraryDetailResponse.Item item : items) {
				UUID placeId = parse(item.placeId());
				boolean lodging = placeId == null || lodgingPlaceIds.contains(placeId);
				if (!lodging && item.lat() != null && item.lng() != null) {
					Integer stay = minutesBetween(item.startsAt(), item.endsAt());
					if (stay != null) {
						stayMin += stay;
						anyTime = true;
					}
					stops.add(new RouteSnapshot.Stop(d, placeId, item.title() == null ? "" : item.title(), item.lat(),
							item.lng(), stay));
					if (!previousWasLodging) {
						if (item.travelDurationMin() != null) {
							travelMin += item.travelDurationMin();
							anyTime = true;
						}
						if (item.travelPath() != null && item.travelPath().size() >= 2) {
							// 🔴 travelPath 는 [경도, 위도](GeoJSON 순서)다. 사본은 [위도, 경도]로 둔다 — 섞으면 점이
							//    지구 반대편으로 튀어 거리가 6,800km 로 나왔다(2026-10-10 운영 확인).
							for (double[] p : item.travelPath()) {
								path.add(new double[] { p[1], p[0] });
							}
						}
						else {
							path.add(new double[] { item.lat(), item.lng() });
						}
					}
					else {
						// 숙소에서 오는 선은 버리고 이 정류장에서 새로 시작한다.
						path.add(new double[] { item.lat(), item.lng() });
					}
				}
				previousWasLodging = lodging;
			}
		}
		if (stops.size() < 2) {
			throw new RoutePostExceptions.InvalidRouteException("TOO_FEW_STOPS");
		}
		int dayCount = dayIndex == null ? (int) stops.stream().mapToInt(RouteSnapshot.Stop::dayIndex).distinct().count() : 1;
		return new RouteSnapshot(parse(detail.tripId()), detail.id(), dayIndex, dayCount, stops, thin(path),
				anyTime ? travelMin + stayMin : null, distance(path));
	}

	static List<double[]> thin(List<double[]> path) {
		if (path.size() <= MAX_PATH_POINTS) {
			return path;
		}
		List<double[]> out = new ArrayList<>(MAX_PATH_POINTS);
		double step = (path.size() - 1) / (double) (MAX_PATH_POINTS - 1);
		for (int i = 0; i < MAX_PATH_POINTS; i++) {
			out.add(path.get((int) Math.round(i * step)));
		}
		return out;
	}

	static Integer distance(List<double[]> path) {
		if (path.size() < 2) {
			return null;
		}
		double meters = 0;
		for (int i = 1; i < path.size(); i++) {
			meters += haversine(path.get(i - 1), path.get(i));
		}
		return (int) Math.round(meters);
	}

	private static double haversine(double[] a, double[] b) {
		double r = 6_371_000;
		double dLat = Math.toRadians(b[0] - a[0]);
		double dLng = Math.toRadians(b[1] - a[1]);
		double h = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(a[0]))
				* Math.cos(Math.toRadians(b[0])) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
		return 2 * r * Math.asin(Math.sqrt(h));
	}

	private static Integer minutesBetween(String start, String end) {
		if (start == null || end == null) {
			return null;
		}
		try {
			long m = Duration.between(OffsetDateTime.parse(start), OffsetDateTime.parse(end)).toMinutes();
			return m >= 0 ? (int) m : null;
		}
		catch (RuntimeException e) {
			return null;
		}
	}

	private static UUID parse(String id) {
		if (id == null) {
			return null;
		}
		try {
			return UUID.fromString(id);
		}
		catch (IllegalArgumentException e) {
			return null;
		}
	}
}
