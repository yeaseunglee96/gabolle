package com.gabolle.backend.recommendation.presentation.dto;

import java.util.List;
import java.util.UUID;

import com.gabolle.backend.recommendation.application.RecommendationActionService;
import com.gabolle.backend.recommendation.domain.RecommendationPlaceAction;

/**
 * 추천 후보에 대한 판단. 화면이 이 값을 받아 하트와 가림 표시를 되살린다.
 *
 * @param decidedByUserId 마지막으로 이 판단을 정한 사람. 동행자가 함께 보는 값이라, 내가 안
 *     뺐는데 후보가 사라졌을 때 누가 정했는지를 보여준다. 계정을 지웠으면 {@code null} 이다
 */
public record RecommendationActionResponse(UUID placeId, RecommendationPlaceAction.Action action,
		UUID decidedByUserId) {

	public static RecommendationActionResponse of(RecommendationPlaceAction action) {
		return new RecommendationActionResponse(action.getPlaceId(), action.getAction(),
				action.getDecidedByUserId());
	}

	/**
	 * 이 여행의 판단 전부 — 동행자가 남긴 것도 함께 온다.
	 *
	 * @param hasMore 상한에 걸려 더 있는데 안 보냈다
	 */
	public record Page(List<RecommendationActionResponse> items, int count, boolean hasMore) {

		public static Page of(RecommendationActionService.Page page) {
			List<RecommendationActionResponse> items = page.items().stream()
					.map(RecommendationActionResponse::of)
					.toList();
			return new Page(items, items.size(), page.hasMore());
		}
	}
}
