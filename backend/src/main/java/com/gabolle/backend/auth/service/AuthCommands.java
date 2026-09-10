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
		 * S15P21E201-317 — {@code sessionToken} 이 있으면 가입과 같은 트랜잭션에서 그 세션이
		 * 만든 여행을 승계한다. {@code X-Session-Token} 헤더가 없던 예전 호출부(테스트 등)를
		 * 깨지 않으려고 4-인자 위 생성자를 남기고, consents·behaviorPersonalizationEnabled 까지
		 * 쓰던 호출부를 위해 이 6-인자 생성자도 남긴다.
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
