package com.gabolle.backend.place.api;

import java.util.List;

/**
 * 목록 조회 공통 응답 — 이름 검색(-462)과 갈래 필터 목록(-473)이 같이 쓴다.
 *
 * <p>🔴 {@code nextCursor} 는 이름 검색에서만 채워진다. 갈래 필터 목록({@code facetType} 으로
 * 들어온 요청)은 커서를 지원하지 않는다 — {@code PlaceRepository.findHavingFeature} 가 정렬은
 * 주지만 오프셋 있는 조회를 지원하지 않고, -473 의 완료 기준도 이어받기를 요구하지 않는다.
 * 필요해지면 그 티켓에서 리포지토리에 오프셋을 추가하고 연다.
 */
public record PlacePageResponse(List<PlaceSummaryResponse> items, int limit, String nextCursor, boolean hasNext) {
}
