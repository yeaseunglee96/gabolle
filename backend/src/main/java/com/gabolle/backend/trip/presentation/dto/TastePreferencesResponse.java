package com.gabolle.backend.trip.presentation.dto;

import java.util.List;

import com.gabolle.backend.trip.domain.PreferenceSnapshot;

/**
 * {@code GET·PUT /api/v1/me/preferences/taste} 응답.
 *
 * <p>한 번도 저장한 적 없으면 404 가 아니라 빈 목록으로 200 을 낸다 — 오류가 아니라 정상 상태다.
 *
 * <p>{@code SPEND_PROFILE} 은 여기 안 담긴다. 그 차원은
 * {@code GET /api/v1/me/preferences/spend} 가 따로 맡는다.
 *
 * @param answers 계정에 저장된 취향. 지워진 차원은 목록에 없다
 */
public record TastePreferencesResponse(List<Answer> answers) {

	/**
	 * @param dimension CHECK 어휘({@code LOCALITY} 등) — 앱이 보낸 camelCase 가 아니다
	 * @param status 여기 담기는 것은 {@code SELECTED} 뿐이다. 지워진 차원은 아예 안 온다
	 * @param value {@code status == "SELECTED"} 일 때만 채워진다
	 */
	public record Answer(String dimension, String status, String value) {
	}

	public static TastePreferencesResponse of(List<PreferenceSnapshot.PreferenceAnswer> answers) {
		return new TastePreferencesResponse(answers.stream()
				.map(a -> new Answer(a.dimension(), a.status().name(), a.valueJson()))
				.toList());
	}
}
