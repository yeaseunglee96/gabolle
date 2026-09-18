package com.gabolle.backend.tripnaming.presentation.dto;

import java.util.List;

/**
 * 여행 이름 후보 — {@code POST /api/v1/trips/{tripId}/name-suggestions} · S15P21E201-1025.
 *
 * @param suggestions 고를 수 있는 이름들. 🔴 <b>전부 이 여행의 일정에 실제로 있는 장소만</b>
 *     담고 있다 — 검사를 통과하지 못한 것은 여기 없다
 * @param source 어디서 나온 이름인가. {@code MODEL} 이면 모델이 지은 것, {@code TEMPLATE} 이면
 *     <b>서버가 일정에서 그대로 만든 것</b>이다.
 *     <p>🔴 이 칸이 있는 이유: 모델이 실패했거나 지어낸 이름이 전부 버려졌을 때도 사용자는
 *     이름을 받는다. 그때 <b>「AI 가 지어 줬다」고 말하면 거짓</b>이 된다. 화면이 그 둘을
 *     다르게 말할 수 있어야 한다
 * @param discardedCount 🔴 모델이 준 것 중 <b>일정에 없는 장소가 들어 있어 버린 개수.</b>
 *     0 이 아니면 모델이 지어내려 했다는 뜻이다 — 사용자 글을 로그에 남기지 않고도
 *     그 일이 얼마나 자주 일어나는지 볼 수 있게 숫자만 싣는다
 */
public record TripNameSuggestionsResponse(List<String> suggestions, String source, int discardedCount) {

	/** 모델이 지었고 검사를 통과했다. */
	public static final String MODEL = "MODEL";

	/** 서버가 일정에서 그대로 만들었다. 지어낸 것이 하나도 없다. */
	public static final String TEMPLATE = "TEMPLATE";
}
