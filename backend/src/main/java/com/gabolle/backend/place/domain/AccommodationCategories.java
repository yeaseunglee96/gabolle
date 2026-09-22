package com.gabolle.backend.place.domain;

import java.util.List;

/**
 * 숙소류를 가리키는 {@code place.category} 값.
 *
 * <p>🔴 2026-09-21 정정. 여기 「숙소를 채우는 적재 코드가 아직 없어 이 값으로 조회하면 지금은
 * 0건이 나오는 것이 정상이다」라고 적혀 있었다. 그때는 맞았지만 지금은 아니다 — 자료는 처음부터
 * 들어와 있었고 갈래 칸만 비어 있었다. {@code TourApiCategory} 가 숙박 대분류를 이 값으로
 * 옮기면서 65곳이 채워졌다 (S15P21E201-1383). 낡은 문장을 남겨 두는 이유는, 이 주석을 읽고
 * 「숙소는 원래 0건이 정상」이라고 믿으면 빈 목록을 버그로 안 보게 되기 때문이다.
 *
 * <p>{@code place.category} 는 값 목록이 확정되지 않은 자유 문자열이라, 숙소를 더 잘게 나누기로
 * 하면 이 목록만 고치면 된다.
 *
 * <p>이 목록은 조회 쪽만 쓰는 것이 아니다. {@code PlaceCandidateQueryService} 가 <b>일반 후보에서
 * 뺄 갈래</b>로도 읽는다 — 숙소는 취향으로 골라지는 것이 아니라 따로 지정하는 것이라, 여기에
 * 값을 더하면 그 갈래는 추천 후보에서도 함께 빠진다.
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
