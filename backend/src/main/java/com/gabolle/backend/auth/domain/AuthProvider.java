package com.gabolle.backend.auth.domain;

public enum AuthProvider {
	GOOGLE,
	NAVER,
	KAKAO,
	/**
	 * Sign in with Apple. 다른 소셜 로그인이 셋 있는 앱은 Apple 심사 지침 4.8 의 예외 대상이
	 * 아니어서, 이것 없이는 iOS 심사를 통과할 수 없다.
	 *
	 * <p>이 enum 에 값을 더할 때는 이름 길이에 주의한다 — {@code auth_identity.provider} 가
	 * {@code VARCHAR(20)} 이다.
	 */
	APPLE
}
