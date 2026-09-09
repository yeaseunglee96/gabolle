package com.gabolle.backend.auth.api;

import jakarta.validation.constraints.NotBlank;

public record LogoutRequest(@NotBlank String refreshToken, boolean allDevices) {
}
