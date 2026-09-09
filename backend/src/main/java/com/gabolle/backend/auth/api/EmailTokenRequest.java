package com.gabolle.backend.auth.api;

import jakarta.validation.constraints.NotBlank;

public record EmailTokenRequest(@NotBlank String token) {
}
