package com.gabolle.backend.trip.presentation.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 씀씀이 성향 답 하나. {@code CreateTripRequest.spendProfile} 과
 * {@code PUT /api/v1/me/preferences/spend} 요청 본문이 같은 모양을 쓴다.
 * {@code dimension} 칸이 없는 것은 그 값이 언제나 {@code SPEND_PROFILE} 로 고정이라서다.
 *
 * @param value {@code answerStatus == "SELECTED"} 일 때만 채운다
 * @param answerStatus {@code SELECTED} · {@code SKIPPED} · {@code UNKNOWN}
 */
public record SpendProfileAnswerInput(String value, @NotBlank String answerStatus) {
}
