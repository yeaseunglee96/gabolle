package com.gabolle.backend.place.api;

import java.util.List;

/**
 * 목록 조회 공통 응답 — 이름 검색과 갈래 필터 목록이 같이 쓴다.
 *
 * <p>{@code nextCursor} 는 이름 검색과 갈래 필터 목록({@code facetType} 으로 들어온 요청)이 채운다 —
 * {@code hasNext} 가 참이면 항상 있다(S15P21E201-2060: 전에는 갈래 필터 목록이 커서 없이 {@code hasNext}
 * 만 참이라 앞 쪽 뒤를 볼 수 없었다). 종류만으로 거른 숙소 후보 목록({@code /accommodations})은 아직 커서를
 * 지원하지 않는다.
 *
 * @param rankTruncated 조건에 맞는 행이 한 요청이 읽는 상한({@code PlaceSearchService.MAX_RANKED} ·
 *     {@code MAX_FACET_SCAN})을 넘어 그 이상은 정렬·페이지 대상에서 아예 빠졌다는 뜻이다. 숙소 후보 목록은
 *     이 방식의 자르기를 하지 않으므로 항상 {@code false} 다.
 */
public record PlacePageResponse(
		List<PlaceSummaryResponse> items, int limit, String nextCursor, boolean hasNext, boolean rankTruncated) {
}
