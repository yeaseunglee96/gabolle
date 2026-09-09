package com.gabolle.backend.auth.api;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record LocalSignupRequest(
		@NotBlank @Email @Size(max = 254) String email,
		@NotBlank @Size(min = 8, max = 100) String password,
		@NotBlank @Size(max = 50) String displayName,
		@NotBlank @Pattern(regexp = "KO|EN") String language,
		@AssertTrue(message = "14세 이상 확인이 필요합니다.") boolean ageGateAccepted,
		@Size(max = 255) String deviceId,
		Map<String, Boolean> consents,
		boolean behaviorPersonalizationEnabled) {

	public LocalSignupRequest(String email, String password, String displayName, String language,
			boolean ageGateAccepted, String deviceId) {
		this(email, password, displayName, language, ageGateAccepted, deviceId, Map.of(), false);
	}
}
