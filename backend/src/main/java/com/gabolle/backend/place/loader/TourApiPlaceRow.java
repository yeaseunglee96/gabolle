package com.gabolle.backend.place.loader;

/**
 * 관광공사 목록 한 줄 — S15P21E201-854.
 *
 * <p>{@code bigData/data/raw/tourapi/tourapi-busan.ndjson} 의 목록 단계에서 오는 항목이다.
 * 상세 단계(영업시간·휴무일)는 이 적재가 읽지 않는다 — 그쪽은 `S15P21E201-852` 다.
 *
 * @param contentId 관광공사가 매긴 식별자. 장소 id 를 이 값에서 계산한다. <b>상가업소번호가 아니다</b>
 * @param contentTypeId 관광지 12 · 문화시설 14 · 축제 15 · 레포츠 28 · 숙박 32 · 쇼핑 38 · 음식점 39
 * @param cat1 원천의 대분류. 갈래 판정이 이 값에서 나온다 (A01 자연 · A02 인문 · A03 레포츠 ·
 *     A04 쇼핑 · A05 음식 · B02 숙박)
 * @param cat3 원천의 소분류. 자연 안에서 해수욕장만 갈라내는 데 쓴다
 * @param title 장소 이름
 * @param address 지번·도로명이 섞여 온다. 원문 그대로 옮긴다
 * @param lat 위도 ({@code mapy})
 * @param lng 경도 ({@code mapx})
 */
public record TourApiPlaceRow(
		String contentId,
		String contentTypeId,
		String cat1,
		String cat3,
		String title,
		String address,
		double lat,
		double lng) {
}
