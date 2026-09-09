package com.gabolle.backend.trip.presentation.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 동행자 초대 발급 요청 — S15P21E201-294.
 *
 * <p>{@code role} 은 문자열로 받아 서비스에서 검증한다({@code TripInviteService.parseInviteRole}).
 * {@code OWNER} 나 그 밖의 값은 400 {@code TRIP_INVITE_ROLE_INVALID} 로 거부된다 — 필수 여부만
 * {@code @NotBlank} 로 여기서 걸러 낸다.
 */
public record CreateTripInviteRequest(@NotBlank String role) {
}
