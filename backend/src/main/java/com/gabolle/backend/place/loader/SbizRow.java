package com.gabolle.backend.place.loader;

/**
 * 상가정보 CSV 한 줄에서 <b>우리가 쓰는 칸만</b> 뽑은 것 — S15P21E201-636.
 *
 * <p>상가정보 = 소상공인시장진흥공단이 분기마다 공개하는 전국 상가·업소 목록
 * (상호·업종·주소·좌표). 부산 2026-06 판이 159,689행이고 그중 대분류가 <b>"음식"</b> 인 것이
 * 53,716행이다.
 *
 * <p>🔴 원본은 39칸이지만 여기 여섯만 있다. 안 쓰는 칸을 들고 다니면 "이 값이 어딘가 쓰이나" 를
 * 매번 확인해야 하고, 실제로는 아무 데도 안 쓰인다.
 *
 * @param storeId 상가업소번호. 🔴 재적재해도 같은 가게가 두 행이 되지 않게 하는 유일한 열쇠다
 * @param name 상호명
 * @param branch 지점명. 없으면 빈 문자열이다 — 이 칸이 있는 음식점이 8,448곳(15.7%)
 * @param subCategory 상권업종<b>소</b>분류명 (백반/한정식·카페·횟집·냉면/밀면 …, 43종).
 *     🔴 <b>중분류(10종)에서 소분류로 바꿨다</b> — S15P21E201-635. 중분류는 자료가 스스로 지어 둔
 *     묶음이라 사람의 판단이 안 들어가서 좋았지만, 그 낱말(한식·일식)이 <b>앱 어디에도 없어서</b>
 *     채점이 한 건도 안 맞았다. 채점기는 앱이 보낸 코드와 이 값을 글자 그대로 비교한다.
 *     소분류에서 앱 코드로 옮기는 규칙은 {@link AppFoodVocabulary} 에 있다
 * @param address 도로명 주소. 없으면 지번 주소
 * @param lat 위도
 * @param lng 경도
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
