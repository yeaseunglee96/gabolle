package com.gabolle.backend.place.domain;

import java.util.List;

/**
 * 숙소류를 가리키는 {@code place.category} 값.
 *
 * <p>숙소를 채우는 적재 코드가 아직 없어 이 값으로 조회하면 지금은 0건이 나오는 것이 정상이다.
 * {@code place.category} 는 값 목록이 확정되지 않은 자유 문자열이라, 숙소 적재가 붙을 때 다른
 * 값을 쓰기로 하면 이 목록만 고치면 된다.
 */
public final class AccommodationCategories {

	/**
	 * 목록으로 둔 이유 — 나중에 숙소 적재가 {@code "HOTEL"}·{@code "GUESTHOUSE"} 처럼
	 * 더 잘게 나눈 값을 쓰기로 해도, 이 목록에 값만 더하면 조회 쪽 코드는 안 바뀐다.
	 */
	public static final List<String> CODES = List.of("LODGING");

	private AccommodationCategories() {
	}
}
