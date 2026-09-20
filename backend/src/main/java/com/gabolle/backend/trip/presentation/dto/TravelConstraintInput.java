package com.gabolle.backend.trip.presentation.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * {@code PUT /api/v1/me/preferences/constraints} 요청 본문.
 *
 * <p>칸 이름이 응답과 다르다 — 응답은 {@code status}, 요청은 {@code answerStatus} 다.
 * 둘을 같은 이름으로 맞추지 않는다. 이미 배포된 앱이 그 이름으로 보내고 있어, 맞추는 순간
 * 그 앱의 요청이 조용히 무시된다.
 *
 * @param value {@code answerStatus == "SAVED"} 일 때만 채운다. JSON 글자 한 덩어리다
 * @param answerStatus {@code SAVED} · {@code LATER} · {@code NEVER}
 */
public record TravelConstraintInput(String value, @NotBlank String answerStatus) {
}
