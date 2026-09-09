package com.gabolle.backend.place.api;

import java.util.List;

/**
 * 목록 조회 공통 응답 — 이름 검색(-462)과 갈래 필터 목록(-473)이 같이 쓴다.
 *
 * <p>🔴 {@code nextCursor} 는 이름 검색에서만 채워진다. 갈래 필터 목록({@code facetType} 으로
 * 들어온 요청)은 커서를 지원하지 않는다 — {@code PlaceRepository.findHavingFeature} 가 정렬은
 * 주지만 오프셋 있는 조회를 지원하지 않고, -473 의 완료 기준도 이어받기를 요구하지 않는다.
 * 필요해지면 그 티켓에서 리포지토리에 오프셋을 추가하고 연다.
 *
 * @param rankTruncated 🔴 이름 검색 경로에서만 의미가 있다 — 조건에 맞는 행이
 *     {@code PlaceSearchService.MAX_RANKED} 를 넘어 그 이상은 정렬·페이지 대상에서 아예 빠졌다는
 *     뜻이다. 조용히 자르면 "정확일치가 왜 안 나오냐" 가 재현 불가능한 버그가 된다 — 그래서 사실을
 *     응답에 싣는다. 갈래 필터 목록 경로는 이 방식의 자르기를 하지 않으므로 항상 {@code false} 다.
 */
public record PlacePageResponse(
		List<PlaceSummaryResponse> items, int limit, String nextCursor, boolean hasNext, boolean rankTruncated) {
}
