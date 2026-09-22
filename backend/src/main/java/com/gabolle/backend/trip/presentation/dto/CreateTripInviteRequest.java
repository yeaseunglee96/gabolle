package com.gabolle.backend.trip.presentation.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 동행자 초대 발급 요청. 여기서는 필수 여부만 보고, 값의 검증은 서비스가 한다 —
 * {@code OWNER} 나 그 밖의 값은 400 {@code TRIP_INVITE_ROLE_INVALID} 로 거부된다.
 */
public record CreateTripInviteRequest(@NotBlank String role) {
}
