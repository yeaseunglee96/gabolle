package com.gabolle.backend.auth.service;

import java.util.Map;

public final class AuthCommands {

	private AuthCommands() {
	}

	public record Register(String email, String password, String displayName, String language,
			boolean ageGateAccepted, String deviceId, Map<String, Boolean> consents,
			boolean behaviorPersonalizationEnabled, String sessionToken) {

		public Register(String email, String password, String displayName, String language,
				boolean ageGateAccepted, String deviceId) {
			this(email, password, displayName, language, ageGateAccepted, deviceId, Map.of(), false, null);
		}

		/**
		 * {@code sessionToken} 이 있으면 가입과 같은 트랜잭션에서 그 세션이 만든 여행을 승계한다.
		 * 짧은 생성자들은 그 토큰을 넘기지 않는 호출부를 위한 것이다.
		 */
		public Register(String email, String password, String displayName, String language,
				boolean ageGateAccepted, String deviceId, Map<String, Boolean> consents,
				boolean behaviorPersonalizationEnabled) {
			this(email, password, displayName, language, ageGateAccepted, deviceId, consents,
					behaviorPersonalizationEnabled, null);
		}
	}

	public record Login(String email, String password, String deviceId) {
	}
}
