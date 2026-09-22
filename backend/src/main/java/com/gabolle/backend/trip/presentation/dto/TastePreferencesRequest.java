package com.gabolle.backend.trip.presentation.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * {@code PUT /api/v1/me/preferences/taste} 요청 본문.
 *
 * <p>보낸 차원만 바뀌고 안 보낸 차원은 그대로 남는다. 지우려면 {@code answerStatus} 를
 * {@code UNKNOWN} 으로 보낸다 — {@code SKIPPED} 는 "물어봤는데 안 답했다" 라서 계정을
 * 안 건드리므로, 둘을 뭉개면 지우기가 불가능해진다.
 *
 * @param answers 바꿀 차원만. 빈 목록이면 아무 일도 일어나지 않는다
 */
public record TastePreferencesRequest(
		@NotNull @Valid List<CreateTripRequest.PreferenceAnswerInput> answers) {
}
