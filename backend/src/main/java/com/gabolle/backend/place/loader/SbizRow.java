package com.gabolle.backend.place.loader;

/**
 * 상가정보 CSV 한 줄에서 우리가 쓰는 칸만 뽑은 것. 상가정보는 소상공인시장진흥공단이 분기마다
 * 공개하는 전국 상가·업소 목록이고, 원본 39칸 중 여기 여섯만 둔다.
 *
 * <p>{@code storeId} 는 상가업소번호로, 재적재해도 같은 가게가 두 행이 되지 않게 하는 유일한
 * 열쇠다. {@code branch} 는 없으면 빈 문자열이고, {@code address} 는 도로명이 없으면 지번이다.
 *
 * <p>{@code subCategory} 는 상권업종 소분류명이다. 중분류는 그 낱말(한식·일식)이 앱
 * 어디에도 없어 채점이 한 건도 안 맞는다 — 채점기는 앱이 보낸 코드와 이 값을 글자 그대로
 * 비교한다. 소분류에서 앱 코드로 옮기는 규칙은 {@link AppFoodVocabulary} 에 있다.
 */
public record SbizRow(
		String storeId,
		String name,
		String branch,
		String subCategory,
		String address,
		double lat,
		double lng) {

	/** 화면과 검색에 쓸 이름. 지점명이 있으면 붙인다 — "케이에프씨" 와 "케이에프씨 코리아" 는 다른 가게다. */
	public String displayName() {
		if (this.branch == null || this.branch.isBlank()) {
			return this.name;
		}
		return this.name + " " + this.branch.trim();
	}
}
