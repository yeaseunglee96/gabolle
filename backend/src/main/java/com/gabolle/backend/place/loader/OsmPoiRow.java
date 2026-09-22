package com.gabolle.backend.place.loader;

/**
 * OpenStreetMap 장소 한 건 — {@code place} 에 넣을 만큼만 추린 모양.
 *
 * <p>원본 태그는 수십 개가 붙어 있지만 여기로 넘어오는 것은 이 다섯뿐이다. 나머지를 들고
 * 다니면 적재기가 「무엇을 쓰고 무엇을 버렸나」를 읽는 사람이 알 수 없다.
 *
 * @param osmId OSM 노드 번호. {@code place.source_id} 로 그대로 들어가고, 이 값에서 장소
 *     아이디를 만든다 — 같은 파일을 두 번 돌려도 행이 두 배가 되지 않게
 * @param address {@code addr:*} 태그를 이어 붙인 것. <b>대부분 없다</b>(13,003곳 중 1,189곳).
 *     없으면 {@code null} 이고, 지어내지 않는다
 */
public record OsmPoiRow(long osmId, String name, String category, String address, double lat, double lng) {
}
