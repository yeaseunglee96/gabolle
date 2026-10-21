package com.gabolle.backend.place.api;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 「맛집」·「카페」 갈래의 구·동 목록 (S15P21E201-2051, 카페는 S15P21E201-2059) — 구 하나에 동 여럿이 딸리고, 곳마다 수가 있다.
 *
 * <p>구 ↔ 동을 한 번에 다 준다. 부산은 구 열여섯에 동 백서른 남짓이라 응답이 몇 KB 이고, 화면이 구를 고를 때마다 서버를
 * 다시 부르지 않아도 된다. 순서는 <b>곳이 많은 쪽이 먼저</b>, 같으면 가나다 순이다 — 화면이 그대로 칩 줄에 놓는다.
 *
 * <p>{@code nearestGu} 는 요청에 좌표({@code lat}·{@code lng})가 왔을 때만 채운다 — 그 갈래의 장소 가운데 <b>가장 가까운 곳의 구</b>다
 * (S15P21E201-2059). 화면은 이 구를 미리 골라 그 구의 「전체」 목록을 바로 보여 준다. 좌표를 안 줬거나, 가장 가까운 곳이
 * 5km 를 넘거나(부산 밖), 그 갈래의 장소가 없으면 {@code null} 이다 — 모르는 것을 아무 구로 채우지 않는다.
 * 좌표는 이 계산에만 쓰고 저장도 기록도 하지 않는다.
 */
public record RestaurantAreaResponse(List<GuItem> areas, String nearestGu, OffsetDateTime generatedAt) {

	/**
	 * @param gu 구·군 이름. 한국어 그대로다 — 이름을 번역하는 일은 화면이 한다
	 * @param placeCount 이 구의 곳 수(장소 기준, 중복 없이)
	 */
	public record GuItem(String gu, long placeCount, List<DongItem> dongs) {
	}

	/**
	 * @param dong 법정동 이름(좌표를 역지오코딩해 얻은 값). 같은 이름이 다른 구에 또 있을 수 있어 구와 함께 읽는다
	 * @param placeCount 이 동의 곳 수
	 */
	public record DongItem(String dong, long placeCount) {
	}
}
