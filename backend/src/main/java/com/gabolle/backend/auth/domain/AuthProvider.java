package com.gabolle.backend.auth.domain;

public enum AuthProvider {
	GOOGLE,
	NAVER,
	KAKAO,
	/**
	 * Sign in with Apple — S15P21E201-825.
	 *
	 * <p>S15P21E201-602 에서 한 번 "추가하지 않음" 으로 정했다가 되돌린 값이다. 다른 소셜 로그인이
	 * 이미 셋 있는 앱은 Apple 심사 지침 4.8 의 예외 대상이 아니어서, 이것 없이는 iOS 심사를 통과할
	 * 수 없다(S15P21E201-819).
	 *
	 * <p>🔴 저장 폭에 주의. {@code auth_identity.provider} 는 {@code VARCHAR(20)} 이라 이름이
	 * 그 안에 들어가야 한다 — {@code APPLE} 은 5자라 마이그레이션 없이 들어간다.
	 */
	APPLE
}
