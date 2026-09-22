package com.gabolle.backend.place.service;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * 장소마다의 대표 메뉴 값(원). 추천 응답과 일정 항목이 같은 값을 써야 하므로 읽는 자리를
 * 하나로 둔다 — 두 벌이 되면 한쪽만 고쳐졌을 때 같은 여행의 추천 가격과 일정 가격이
 * 어긋나고, 그 어긋남은 어느 화면에도 안 보인다.
 *
 * <p>{@link OpeningHoursFilterPort} 와 달리 <b>여럿을 한 번에</b> 묻는다. 부르는 자리가 추천
 * 20곳·일정 하루치처럼 언제나 묶음이라, 하나씩 물으면 그만큼 질의가 늘어난다.
 *
 * <p>🔴 <b>모르는 곳은 표에 아예 없다.</b> {@code null} 이나 {@code 0} 을 넣어 돌려주지
 * 않는다 — 부르는 쪽이 「없음」과 「0원(무료)」을 가를 수 있어야 한다.
 */
public interface PlaceMenuPricePort {

	/**
	 * @param placeIds 묻는 장소들. 비어 있거나 {@code null} 이면 빈 표
	 * @return 값이 있는 장소만 담은 표. 없는 장소는 열쇠 자체가 없다
	 */
	Map<UUID, Integer> pricesOf(Collection<UUID> placeIds);
}
