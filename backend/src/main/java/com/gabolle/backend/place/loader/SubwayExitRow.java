package com.gabolle.backend.place.loader;

/**
 * 지하철 출구 안내 산출물 한 줄. {@code namespace} 는 장소가 어느 적재기로 들어왔나이고
 * ({@code "SBIZ"} 또는 {@code "TOURAPI"}), {@link PlaceFeatureLoader#placeIdOf} 가 이 값으로
 * 장소를 찾는다. {@code storeId} 는 그 출처 안에서의 식별자다.
 */
record SubwayExitRow(String namespace, String storeId, String subwayExit) {
}
