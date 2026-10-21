package com.gabolle.backend.routepost;

import java.util.UUID;

/**
 * 내 일정에서 루트 사본을 뜬다. 일정 패키지와의 이음매다 — 시험은 이것을 가짜로 바꿔 끼워
 * 일정 전체(추천 엔진·경로 계산)를 띄우지 않는다.
 */
public interface RouteSnapshotSource {

	/**
	 * @throws RoutePostExceptions.SourceNotFoundException 일정이 없거나 이 사람이 그 여행의 일원이 아닐 때
	 * @throws RoutePostExceptions.InvalidRouteException 올릴 수 없는 경로일 때
	 */
	RouteSnapshot snapshot(UUID user, String itineraryId, Integer dayIndex);
}
