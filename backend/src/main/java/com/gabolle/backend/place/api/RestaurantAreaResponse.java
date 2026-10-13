package com.gabolle.backend.place.api;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 「맛집」 갈래의 구·동 목록 (S15P21E201-2051) — 구 하나에 동 여럿이 딸리고, 곳마다 맛집 수가 있다.
 *
 * <p>구 ↔ 동을 한 번에 다 준다. 부산은 구 열여섯에 동 백서른 남짓이라 응답이 몇 KB 이고, 화면이 구를 고를 때마다 서버를
 * 다시 부르지 않아도 된다. 순서는 <b>식당이 많은 곳이 먼저</b>, 같으면 가나다 순이다 — 화면이 그대로 칩 줄에 놓는다.
 */
public record RestaurantAreaResponse(List<GuItem> areas, OffsetDateTime generatedAt) {

	/**
	 * @param gu 구·군 이름. 한국어 그대로다 — 이름을 번역하는 일은 화면이 한다
	 * @param placeCount 이 구의 맛집 수(장소 기준, 중복 없이)
	 */
	public record GuItem(String gu, long placeCount, List<DongItem> dongs) {
	}

	/**
	 * @param dong 법정동 이름(좌표를 역지오코딩해 얻은 값). 같은 이름이 다른 구에 또 있을 수 있어 구와 함께 읽는다
	 * @param placeCount 이 동의 맛집 수
	 */
	public record DongItem(String dong, long placeCount) {
	}
}
