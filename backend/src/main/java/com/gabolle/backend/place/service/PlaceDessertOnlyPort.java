package com.gabolle.backend.place.service;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * 음식 종류 표식이 디저트(CAFE_DESSERT) 하나뿐인 곳 — S15P21E201-1635.
 *
 * <p>일정 조립이 이런 밥집을 끼니로 세지 않고 카페로 본다. 상가 자료가 젤라또·아이스크림·빵집을 「음식점」으로 넣어서
 * 「점심으로 젤라또」가 됐다. 데이터는 마이그레이션이 고쳤지만 다음 적재 때 다시 밥집으로 들어올 수 있어 코드가 막는다.
 */
public interface PlaceDessertOnlyPort {

	/**
	 * @param placeIds 묻는 장소들. 비어 있으면 빈 집합
	 * @return 그중 음식 종류 표식이 디저트 하나뿐인 곳. 표식이 없는 곳은 안 든다
	 */
	Set<UUID> dessertOnly(Collection<UUID> placeIds);
}
