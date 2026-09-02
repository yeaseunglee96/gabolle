package com.gabolle.backend.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(@NotBlank @Email String email, @NotBlank String password, @Size(max = 255) String deviceId) {
}
