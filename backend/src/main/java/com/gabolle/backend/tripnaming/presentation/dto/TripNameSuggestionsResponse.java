package com.gabolle.backend.tripnaming.presentation.dto;

import java.util.List;

/**
 * 여행 이름 후보 — {@code POST /api/v1/trips/{tripId}/name-suggestions}.
 *
 * @param suggestions 고를 수 있는 이름들. 전부 이 여행의 일정에 실제로 있는 장소만 담고
 *     있다 — 검사를 통과하지 못한 것은 여기 없다
 * @param source {@code MODEL} 이면 모델이 지은 것, {@code TEMPLATE} 이면 서버가 일정에서
 *     그대로 만든 것이다. 화면은 둘을 다르게 말해야 한다
 * @param discardedCount 모델이 준 것 중 일정에 없는 장소가 들어 있어 버린 개수
 */
public record TripNameSuggestionsResponse(List<String> suggestions, String source, int discardedCount) {

	/** 모델이 지었고 검사를 통과했다. */
	public static final String MODEL = "MODEL";

	/** 서버가 일정에서 그대로 만들었다. 지어낸 것이 하나도 없다. */
	public static final String TEMPLATE = "TEMPLATE";
}
