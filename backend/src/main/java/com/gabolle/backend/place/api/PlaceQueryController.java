package com.gabolle.backend.place.api;

import java.util.List;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.place.domain.AccommodationCategories;
import com.gabolle.backend.place.service.CertifiedRestaurantQueryService;
import com.gabolle.backend.place.service.PlaceCategoryService;
import com.gabolle.backend.place.service.PlaceFacetService;
import com.gabolle.backend.place.service.PlaceRequestException;
import com.gabolle.backend.place.service.PlaceSearchService;

/**
 * 장소 이름 검색과 표식 기준 갈래 조회.
 *
 * <p>장소 목록·갈래는 공개 카탈로그라 "누가 요청했는가" 가 필요 없다. 그래서
 * {@code Authentication} 파라미터도 {@code X-User-Id} 헤더도 여기 없다.
 *
 * <p>{@code category} 와 {@code facetType}/{@code facetKey} 를 같은 이름으로 합치지 않는다.
 * {@code category} 는 {@code place.category} 자유 문자열 칼럼이고 {@code facetType}/{@code facetKey}
 * 는 {@code place_feature} 표식이다 — 같은 이름을 쓰면 "카테고리로 걸렀는데 표식 없는 곳이 나온다"
 * 가 재현하기 어려운 버그가 된다.
 */
@RestController
@RequestMapping("/api/v1/places")
@Profile({"db", "dev"})
public class PlaceQueryController {

	private final PlaceSearchService placeSearchService;

	private final PlaceFacetService placeFacetService;

	private final PlaceCategoryService placeCategoryService;

	private final CertifiedRestaurantQueryService certifiedRestaurantQueryService;

	public PlaceQueryController(PlaceSearchService placeSearchService, PlaceFacetService placeFacetService,
			PlaceCategoryService placeCategoryService,
			CertifiedRestaurantQueryService certifiedRestaurantQueryService) {
		this.placeSearchService = placeSearchService;
		this.placeFacetService = placeFacetService;
		this.placeCategoryService = placeCategoryService;
		this.certifiedRestaurantQueryService = certifiedRestaurantQueryService;
	}

	/**
	 * 지금 장소가 있는 갈래와 그 수. 추천 후보를 좁힐 때 실제로 비교되는 값이
	 * {@code place.category} 라, 취향 화면이 여기 없는 갈래를 고르면 후보 0 으로 일정 생성이
	 * 실패한다. {@code facets} 와 다른 것이다 — 저쪽은 {@code place_feature} 표식이다.
	 */
	@GetMapping("/categories")
	public ApiResponse<PlaceCategoryResponse> categories(
			@RequestHeader(value = "X-Request-Id", required = false) String requestId) {
		return ApiResponse.success(this.placeCategoryService.categories(), resolveRequestId(requestId));
	}

	/**
	 * 이름 검색과 표식 필터 목록을 한 엔드포인트에서 받는다. {@code query} 와 {@code facetType}
	 * 중 정확히 하나만 와야 한다 — 이름 검색은 정확일치·접두일치·포함 순위가 있고 표식 목록은
	 * 없어서 처리 경로가 완전히 갈린다.
	 */
	@GetMapping
	public ApiResponse<PlacePageResponse> list(
			@RequestParam(required = false) String query,
			@RequestParam(required = false) String category,
			@RequestParam(required = false) Integer limit,
			@RequestParam(required = false) String cursor,
			@RequestParam(required = false) String facetType,
			@RequestParam(required = false) String facetKey,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId) {

		boolean hasQuery = query != null && !query.isBlank();
		boolean hasFacet = facetType != null && !facetType.isBlank();
		if (hasQuery == hasFacet) {
			throw new PlaceRequestException("INVALID_REQUEST", "검색어와 갈래 중 하나만 지정해 주세요.",
					List.of("query", "facetType"));
		}

		PlacePageResponse page = hasQuery
				? this.placeSearchService.search(query, category, limit, cursor)
				: this.placeSearchService.searchByFacet(facetType, facetKey, limit);

		return ApiResponse.success(page, resolveRequestId(requestId));
	}

	/**
	 * 숙소 후보 조회. {@code place.category} 가 숙소류인 장소만 돌려준다. 적재된 자료에 숙소가
	 * 없어 빈 목록이 나오는 것이 정상이다 ({@code AccommodationCategories} 클래스 참고).
	 */
	@GetMapping("/accommodations")
	public ApiResponse<PlacePageResponse> accommodations(
			@RequestParam(required = false) Integer limit,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId) {

		PlacePageResponse page = this.placeSearchService.listByCategories(
				AccommodationCategories.CODES, limit);
		return ApiResponse.success(page, resolveRequestId(requestId));
	}

	/** 갈래 목록과 건수. */
	@GetMapping("/facets")
	public ApiResponse<PlaceFacetResponse> facets(
			@RequestHeader(value = "X-Request-Id", required = false) String requestId) {
		return ApiResponse.success(this.placeFacetService.facets(), resolveRequestId(requestId));
	}

	/**
	 * 「맛집」 갈래의 구 → 동 목록과 곳마다의 맛집 수(S15P21E201-2051). 공공기관이 인증한 식당만 센다. 맛집 자료를 아직 안
	 * 넣었으면 빈 목록이다.
	 */
	@GetMapping("/restaurants/areas")
	public ApiResponse<RestaurantAreaResponse> restaurantAreas(
			@RequestHeader(value = "X-Request-Id", required = false) String requestId) {
		return ApiResponse.success(this.certifiedRestaurantQueryService.areas(), resolveRequestId(requestId));
	}

	/**
	 * 구·동으로 거른 맛집 목록(S15P21E201-2051). {@code gu} 는 필수이고 {@code dong} 을 빼면 구 전체다. 인증한 목록이 많은 집이
	 * 먼저, 같으면 이름 순이다. 각 줄은 {@code /places?facetType=…} 목록과 같은 칸에 {@code gu}·{@code dong}·{@code certifiedSources}
	 * 를 더한 모양이다.
	 */
	@GetMapping("/restaurants")
	public ApiResponse<RestaurantPageResponse> restaurants(
			@RequestParam(required = false) String gu,
			@RequestParam(required = false) String dong,
			@RequestParam(required = false) Integer limit,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId) {
		return ApiResponse.success(this.certifiedRestaurantQueryService.restaurants(gu, dong, limit),
				resolveRequestId(requestId));
	}

	private String resolveRequestId(String requestId) {
		return requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId;
	}
}
