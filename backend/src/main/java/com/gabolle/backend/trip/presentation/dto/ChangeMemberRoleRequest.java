package com.gabolle.backend.trip.presentation.dto;

import jakarta.validation.constraints.NotBlank;

/** 참여자 역할 변경 요청. {@code EDITOR}·{@code VIEWER} 만 받는다. */
public record ChangeMemberRoleRequest(@NotBlank String role) {
}
