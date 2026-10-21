package com.gabolle.backend.facility.api;

import java.util.List;

/**
 * 근처 편의시설 조회 응답 (S15P21E201-2052).
 *
 * @param kind 요청한 종류 — {@code PARKING} · {@code PHARMACY} · {@code TOILET} · {@code LOCKER}
 * @param lat 기준 위도(요청 그대로)
 * @param lng 기준 경도
 * @param radiusM 쓴 반경(m)
 * @param stayMinutes 주차장 예상요금에 쓴 체류 시간(분). 주차장이 아니면 null
 * @param total 반경 안에서 조건에 맞는 곳의 수 — {@code limit} 으로 자르기 전이다. 화면이 「더 있다」를 알린다
 * @param limit 쓴 개수 상한
 * @param asOf 「지금 여는가」를 판정한 서울 시각. 어느 시각 기준의 답인지 남긴다
 * @param items 정렬된 목록 — 주차장은 예상요금이 싼 순(요금을 모르는 곳은 맨 뒤), 약국은 지금 연 곳 먼저, 나머지는 가까운 순
 */
public record FacilityNearbyResponse(String kind, double lat, double lng, int radiusM, Integer stayMinutes, int total,
		int limit, String asOf, List<Item> items) {

	/**
	 * 한 곳.
	 *
	 * @param id 행 번호(UUID 글자)
	 * @param address 주소. 모르면 null
	 * @param distanceM 기준점에서의 직선 거리(m, 반올림)
	 * @param detail 종류별 칸 — {@link FacilityDetail}
	 */
	public record Item(String id, String kind, String name, String address, double lat, double lng, int distanceM,
			FacilityDetail detail) {
	}
}
