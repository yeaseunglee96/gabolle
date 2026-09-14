package com.gabolle.backend.trip.presentation.dto;

import java.util.List;

import com.gabolle.backend.trip.domain.PreferenceSnapshot;

/**
 * {@code GET·PUT /api/v1/me/preferences/taste} 응답 — S15P21E201-639.
 *
 * <p>🔴 한 번도 저장한 적 없으면 <b>404 가 아니라 빈 목록으로 200</b> 을 낸다.
 * "계정 기본 취향이 아직 없다" 는 오류가 아니라 정상 상태다 — 404 로 답하면 화면이
 * 그것을 오류로 다뤄야 하고, 첫 실행인 사람에게 빨간 화면이 뜬다.
 * ({@code SpendProfileResponse.unknown()} 이 같은 까닭으로 있다.)
 *
 * <p>🔴 {@code SPEND_PROFILE} 은 여기 <b>안 담긴다.</b> 그 차원은
 * {@code GET /api/v1/me/preferences/spend} 가 따로 맡는다 — 두 화면이 같은 값을 각자
 * 그리면 한쪽만 고쳤을 때 어긋난다.
 *
 * @param answers 계정에 저장된 취향. 지워진 차원은 목록에 없다
 */
public record TastePreferencesResponse(List<Answer> answers) {

	/**
	 * @param dimension CHECK 어휘({@code LOCALITY} 등) — 앱이 보낸 camelCase 가 아니다.
	 *        화면이 되돌려 받을 때 어느 칸인지 헷갈리지 않게 한 쪽으로 맞춘다
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
