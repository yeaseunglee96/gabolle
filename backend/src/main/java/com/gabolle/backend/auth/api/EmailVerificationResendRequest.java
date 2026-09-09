package com.gabolle.backend.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record EmailVerificationResendRequest(@NotBlank @Email String email) {
}
