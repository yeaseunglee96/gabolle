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
 *
 * @param decidedByUserId 마지막으로 이 판단을 정한 사람. 🔴 <b>동행자가 함께 보는 값이라
 *     이 칸이 필요하다</b> — 내가 안 뺐는데 후보가 사라져 있을 때, 누가 정했는지를 못 보면
 *     화면은 "왜 사라졌는지 알 수 없음" 만 그린다. 그 사람이 계정을 지웠으면 {@code null} 이다
 */
public record RecommendationActionResponse(UUID placeId, RecommendationPlaceAction.Action action,
		UUID decidedByUserId) {

	public static RecommendationActionResponse of(RecommendationPlaceAction action) {
		return new RecommendationActionResponse(action.getPlaceId(), action.getAction(),
				action.getDecidedByUserId());
	}

	/** 한 여행의 판단 전부 — 동행자가 남긴 것도 함께 온다. */
	public record Page(List<RecommendationActionResponse> items, int count) {

		public static Page of(List<RecommendationPlaceAction> actions) {
			List<RecommendationActionResponse> items = actions.stream()
					.map(RecommendationActionResponse::of)
					.toList();
			return new Page(items, items.size());
		}
	}
}
