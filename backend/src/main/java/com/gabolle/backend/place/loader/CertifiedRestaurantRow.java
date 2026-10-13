package com.gabolle.backend.place.loader;

import java.util.List;
import java.util.UUID;

import com.gabolle.backend.place.domain.CertifiedRestaurant;

/**
 * 공공기관이 인증한 식당 한 곳 — {@link CertifiedRestaurantReader} 가 읽은 한 줄이다 (S15P21E201-2051).
 *
 * <p>{@code sourceId} 는 수집한 쪽이 매긴 번호이고(부산시 맛집정보 {@code BF…}, 모범음식점 {@code MB…}, 택슐랭
 * {@code TX…}) 새 장소의 id 를 이 값에서 계산한다. {@code gu}·{@code dong} 은 좌표를 VWorld(국토교통부 지도 서비스)로
 * 역지오코딩(좌표로 행정 주소를 알아내는 것)해서 얻은 <b>법정동</b>이다 — 주소 글에 적힌 동이 아니다.
 * {@code existingPlaceId} 가 있으면 그 식당은 우리 DB 에 이미 있는 장소이고, 표식만 달고 장소는 새로 만들지 않는다.
 *
 * <p>{@code tel}·{@code menu} 는 읽기만 하고 아직 DB 에 넣지 않는다 — {@code place} 에 전화번호 칸이 없고, 메뉴는
 * 「물밀면 ￦9,000」처럼 가격이 글에 섞여 있어 구조화한 뒤 {@code MENU_ITEMS} 로 넣는 별개의 일이다. 파일에 남겨 두는
 * 것은 나중 결정이 원문을 다시 받지 않아도 되게 하려는 것이다.
 */
public record CertifiedRestaurantRow(
		String sourceId,
		String name,
		double lat,
		double lng,
		String address,
		String tel,
		String gu,
		String dong,
		List<CertifiedRestaurant.Source> sources,
		String menu,
		UUID existingPlaceId,
		String photoUrl,
		String photoSource) {

	public CertifiedRestaurantRow {
		sources = List.copyOf(sources);
	}

	/** 표식의 구·동. */
	public CertifiedRestaurant.Area area() {
		return new CertifiedRestaurant.Area(this.gu, this.dong);
	}
}
