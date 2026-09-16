package com.gabolle.backend.place.loader;

/**
 * 지하철 출구 안내 산출물 한 줄 — S15P21E201-479.
 *
 * @param namespace 장소가 어느 적재기로 들어왔나 — {@code "SBIZ"} 또는 {@code "TOURAPI"}.
 *     {@link PlaceFeatureLoader#placeIdOf} 가 이 값으로 장소를 찾는다
 * @param storeId 그 출처 안에서의 식별자 (상가업소번호 또는 contentid)
 * @param subwayExit 예: {@code "2호선 강남역 3번 출구"}
 */
record SubwayExitRow(String namespace, String storeId, String subwayExit) {
}
