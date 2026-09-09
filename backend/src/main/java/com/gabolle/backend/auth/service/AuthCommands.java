package com.gabolle.backend.auth.service;

import java.util.Map;

public final class AuthCommands {

	private AuthCommands() {
	}

	public record Register(String email, String password, String displayName, String language,
			boolean ageGateAccepted, String deviceId, Map<String, Boolean> consents,
			boolean behaviorPersonalizationEnabled) {

		public Register(String email, String password, String displayName, String language,
				boolean ageGateAccepted, String deviceId) {
			this(email, password, displayName, language, ageGateAccepted, deviceId, Map.of(), false);
		}
	}

	public record Login(String email, String password, String deviceId) {
	}
}
