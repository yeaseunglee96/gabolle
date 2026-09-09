package com.gabolle.backend.auth.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshTokenRequest(@NotBlank String refreshToken, @Size(max = 255) String deviceId) {
}
