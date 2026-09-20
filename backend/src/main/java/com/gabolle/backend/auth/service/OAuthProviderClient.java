package com.gabolle.backend.auth.service;

import com.gabolle.backend.auth.domain.AuthProvider;

public interface OAuthProviderClient {

	AuthProvider provider();

	OAuthUserProfile exchangeAuthorizationCode(String authorizationCode, String redirectUri, String codeVerifier,
			String state, String nonce);

	// emailVerified·emailValid 가 Boolean 인 것은 null 이 "모른다" 이기 때문이다.
	// 구글은 검증 실패면 로그인 자체가 막히므로 항상 true, 카카오는 kakao_account 의
	// is_email_verified·is_email_valid 를 그대로 옮기고(필드가 없으면 null),
	// 네이버는 이 정보를 응답에 안 담아 항상 null 이다.
	record OAuthUserProfile(String subject, String email, String displayName, String language,
			Boolean emailVerified, Boolean emailValid) {
	}
}
