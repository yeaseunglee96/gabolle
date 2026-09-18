package com.gabolle.backend.trip.presentation.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 씀씀이 성향 답 하나 — S15P21E201-709. {@code CreateTripRequest.spendProfile} 과
 * {@code PUT /api/v1/me/preferences/spend} 요청 본문이 같은 모양을 쓴다.
 *
 * <p>{@code PreferenceAnswerInput} 과 모양이 같지만 {@code dimension} 이 없다 — 그 값은
 * 언제나 {@code SPEND_PROFILE} 로 고정이라 요청자가 보낼 필요가 없다.
 *
 * @param value {@code answerStatus == "SELECTED"} 일 때만 채운다.
 * @param answerStatus {@code SELECTED} · {@code SKIPPED} · {@code UNKNOWN}
 */
public record SpendProfileAnswerInput(String value, @NotBlank String answerStatus) {
}
