package com.gabolle.backend.facility.service;

import java.util.List;

/**
 * 근처 환전소 찾기 (S15P21E201-2066).
 *
 * <p>다른 편의시설과 달리 우리 표에 싣지 않고 그때그때 바깥(카카오 로컬)에 묻는다 — 환전소 목록을 내려받을 공공데이터가 없다
 * (2026-10-20 공공데이터포털 「환전」·「외국환」 검색: 지역화폐 환전 대행점뿐).
 */
public interface ExchangeSearch {

	/**
	 * 기준점에서 반경 안의 환전소. 가까운 순.
	 *
	 * @throws IllegalStateException 바깥 호출이 실패했을 때
	 */
	List<Spot> search(double lat, double lng, int radiusM, int limit);

	/**
	 * 한 곳.
	 *
	 * @param id 카카오 장소 번호
	 * @param address 도로명 주소, 없으면 지번 주소. 둘 다 없으면 null
	 * @param tel 전화. 모르면 null
	 * @param placeUrl 카카오맵 장소 주소 — 영업시간·후기는 거기 있다
	 * @param distanceM 기준점에서의 거리(m) — 카카오가 준 값
	 */
	record Spot(String id, String name, String address, double lat, double lng, String tel, String placeUrl,
			int distanceM) {
	}
}
