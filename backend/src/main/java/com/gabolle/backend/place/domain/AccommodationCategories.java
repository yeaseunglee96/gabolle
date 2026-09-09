package com.gabolle.backend.place.domain;

import java.util.List;

/**
 * 숙소류를 가리키는 {@code place.category} 값 — S15P21E201-456.
 *
 * <h2>🔴 지금 적재된 자료에는 숙소가 한 곳도 없다</h2>
 *
 * {@code SbizPlaceLoader}(상가정보 적재, S15P21E201-636)는 대분류가 "음식" 인 행만
 * 넣고 {@code category} 를 항상 {@code "FOOD"} 로 채운다 — 그 클래스 javadoc 참고.
 * 숙소를 채우는 적재 코드는 저장소 어디에도 아직 없다. 그래서 이 값으로 조회하면
 * 지금은 <b>0건이 나오는 것이 사실이다</b> — {@code CATEGORY} 취향의 SEA_BEACH·CITY·
 * CULTURE_TEMPLE·NATURE_WALK 가 이미 같은 처지다(SbizPlaceLoader 참고).
 *
 * <h2>🔴 {@code "LODGING"} 을 고른 근거</h2>
 *
 * {@link Place} 클래스 javadoc 이 가리키는 참고 코드({@code ref/local-route} Prisma
 * {@code Place} 모델)의 {@code category} 칸 주석이 이미
 * {@code "TOURIST | RESTAURANT | CAFE | LODGING"} 라고 값 목록을 적어 뒀다 — 이 팀이
 * 전신 프로젝트에서 숙소를 가리키던 낱말이 {@code LODGING} 이다. {@code place.category}
 * 는 아직 값 목록이 확정되지 않은 자유 문자열이라({@code V20260904000000} 마이그레이션
 * 주석) 이 낱말을 팀이 대신 확정하는 것은 아니다 — 숙소 적재가 실제로 붙을 때 그 팀이
 * 다른 값을 쓰기로 하면 이 목록만 고치면 된다.
 *
 * <p>🔴 <b>완전히 새 메커니즘을 만들지 않는다.</b> {@code PlaceCandidateRequest.categories}
 * ·{@code NearbyPlaceService.PurposeSpec.categories()}·{@code PlaceRepository.
 * searchByNameAndCategory} 가 이미 "categories: 자유 문자열 목록으로 거른다" 는 패턴을
 * 쓰고 있고, 이 목록도 그 패턴 위에 얹는다({@link PlaceRepository#findByCategoryIn}).
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
