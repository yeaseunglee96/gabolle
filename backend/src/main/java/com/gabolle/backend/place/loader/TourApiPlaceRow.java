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
 * @param firstImage 대표 사진 주소 ({@code firstimage}). 없으면 {@code null}
 * @param copyrightType 사진 저작권 유형 ({@code cpyrhtDivCd}). {@code Type1}(공공누리 제1유형 —
 *     출처를 표시하면 자유 이용) 과 {@code Type3}(제3자 저작물 — 재사용 전 저작권자의 별도 허락이
 *     필요) 이 섞여 온다. <b>호출자가 반드시 이 값을 보고 걸러야 한다</b> — {@link TourApiPlaceLoader}
 *     참고. 실측(2026-09-15): 사진 있는 546곳 중 Type3 가 468곳(86%), Type1 은 78곳뿐이다
 */
public record TourApiPlaceRow(
		String contentId,
		String contentTypeId,
		String cat1,
		String cat3,
		String title,
		String address,
		double lat,
		double lng,
		String firstImage,
		String copyrightType) {
}
