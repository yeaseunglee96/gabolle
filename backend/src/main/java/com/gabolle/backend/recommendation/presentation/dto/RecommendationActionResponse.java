package com.gabolle.backend.recommendation.presentation.dto;

import java.util.List;
import java.util.UUID;

import com.gabolle.backend.recommendation.domain.RecommendationPlaceAction;

/**
 * 추천 후보에 대한 판단 — S15P21E201-1013.
 *
 * <p>화면이 이 값을 받아 하트와 가림 표시를 되살린다. 모양은 {@code FestivalResponse}·
 * {@code ItineraryVersionsResponse} 와 같게 뒀다({@code items}·{@code count}) — 목록 응답마다
 * 다른 모양을 만들면 화면이 경로마다 다르게 읽어야 한다.
 */
public record RecommendationActionResponse(UUID placeId, RecommendationPlaceAction.Action action) {

	public static RecommendationActionResponse of(RecommendationPlaceAction action) {
		return new RecommendationActionResponse(action.getPlaceId(), action.getAction());
	}

	/** 한 여행에서 내가 내린 판단 전부. */
	public record Page(List<RecommendationActionResponse> items, int count) {

		public static Page of(List<RecommendationPlaceAction> actions) {
			List<RecommendationActionResponse> items = actions.stream()
					.map(RecommendationActionResponse::of)
					.toList();
			return new Page(items, items.size());
		}
	}
}
