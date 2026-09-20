package com.gabolle.backend.place.loader;

/**
 * 관광공사 목록 한 줄. {@code contentId} 는 관광공사가 매긴 식별자이고 상가업소번호가 아니다 —
 * 장소 id 를 이 값에서 계산한다. {@code contentTypeId} 는 관광지 12 · 문화시설 14 · 축제 15 ·
 * 레포츠 28 · 숙박 32 · 쇼핑 38 · 음식점 39, {@code cat1} 은 A01 자연 · A02 인문 · A03 레포츠 ·
 * A04 쇼핑 · A05 음식 · B02 숙박이다. {@code lat} 은 {@code mapy}, {@code lng} 는 {@code mapx}
 * 에서 오고, {@code address} 는 지번·도로명이 섞여 오는 것을 원문 그대로 옮긴다.
 *
 * <p>{@code copyrightType} 은 자유 이용인 것과 제3자 저작물이 섞여 오므로 호출자가 반드시 이
 * 값을 보고 걸러야 한다 — {@link TourApiPlaceLoader} 참고.
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
