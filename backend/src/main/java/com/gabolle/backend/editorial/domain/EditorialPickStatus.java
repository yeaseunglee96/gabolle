package com.gabolle.backend.editorial.domain;

/**
 * 발행 상태. 값 이름이 {@code ck_editorial_pick_status} 와 같아야 한다.
 */
public enum EditorialPickStatus {

	/**
	 * 아직 안 내보낸다. 번역이 없거나 장소가 덜 채워진 판이 여기 머문다 — 제목 두 언어가
	 * 필수인 것과 같은 이유다. 반쯤 된 Pick 을 내보내면 그것이 기준선이 된다.
	 */
	DRAFT,

	/**
	 * 지금 내보내는 판. 한 {@code pickKey} 에 하나뿐이다
	 * ({@code uq_editorial_pick_published} 부분 유니크 인덱스).
	 */
	PUBLISHED,

	/**
	 * 물러난 판. 지우지 않는다 — 이미 나간 추천 결과가 이 판을 가리키고 있고,
	 * 지우면 "그때 무엇을 보여줬나" 에 답할 수 없다.
	 */
	RETIRED
}
