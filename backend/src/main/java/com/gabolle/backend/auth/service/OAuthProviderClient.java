package com.gabolle.backend.auth.service;

import com.gabolle.backend.auth.domain.AuthProvider;

public interface OAuthProviderClient {

	AuthProvider provider();

	OAuthUserProfile exchangeAuthorizationCode(String authorizationCode, String redirectUri, String codeVerifier,
			String state, String nonce);

	// S15P21E201-741 — emailVerified·emailValid 는 Boolean(원시형 boolean 이 아니다).
	// null 은 "모른다" 다. 구글은 검증 실패 시 로그인 자체가 막히므로 항상 true,
	// 카카오는 kakao_account 응답의 is_email_verified·is_email_valid 를 그대로 옮긴 값
	// (필드가 없으면 null), 네이버는 이 정보를 응답에서 아예 안 줘서 항상 null이다.
	record OAuthUserProfile(String subject, String email, String displayName, String language,
			Boolean emailVerified, Boolean emailValid) {
	}
}
