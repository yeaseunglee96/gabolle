package com.gabolle.backend.auth.service;

import java.util.List;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.gabolle.backend.auth.api.OAuthLoginRequest;
import com.gabolle.backend.auth.domain.AuthProvider;

/**
 * 소셜 로그인 흐름 조정 — PKCE challenge 소비 → provider 코드 교환 → 계정 판정.
 *
 * <p>14세 확인은 여기서 보지 않는다. 그것은 가입 조건이라 가입 단계가 보고, 옛 앱이 첫 요청에
 * 실어 보낸 값은 {@link OAuthAccountService#authenticate} 가 "한 번에 가입" 판정에 쓴다.
 */
@Service
@Profile({"db", "dev"})
public class OAuthLoginService {

	private final List<OAuthProviderClient> clients;
	private final OAuthChallengeService challengeService;
	private final OAuthAccountService accountService;

	public OAuthLoginService(List<OAuthProviderClient> clients, OAuthChallengeService challengeService,
			OAuthAccountService accountService) {
		this.clients = clients;
		this.challengeService = challengeService;
		this.accountService = accountService;
	}

	public OAuthAccountService.Outcome login(AuthProvider provider, OAuthLoginRequest request) {
		OAuthProviderClient.OAuthUserProfile profile = exchange(provider, request);
		return accountService.authenticate(provider, profile, request.deviceId(), request.consents(),
				request.behaviorPersonalizationEnabledOrFalse(), Boolean.TRUE.equals(request.ageGateAccepted()));
	}

	/** 로그인한 계정에 소셜 신원을 붙인다. 인증 코드 교환은 로그인과 같다. */
	public OAuthAccountService.LinkedIdentity linkForUser(UUID userId, AuthProvider provider, OAuthLoginRequest request) {
		OAuthProviderClient.OAuthUserProfile profile = exchange(provider, request);
		return accountService.linkAuthenticated(userId, provider, profile);
	}

	private OAuthProviderClient.OAuthUserProfile exchange(AuthProvider provider, OAuthLoginRequest request) {
		OAuthProviderClient client = clients.stream().filter(candidate -> candidate.provider() == provider).findFirst()
				.orElseThrow(() -> new AuthException("OAUTH_PROVIDER_NOT_CONFIGURED", "소셜 로그인 제공자가 아직 설정되지 않았습니다.",
						HttpStatus.NOT_IMPLEMENTED));
		challengeService.consume(provider, request.state(), request.nonce(), request.codeVerifier(), request.redirectUri(),
				request.deviceId());
		OAuthProviderClient.OAuthUserProfile profile = client.exchangeAuthorizationCode(request.authorizationCode(),
				request.redirectUri(), request.codeVerifier(), request.state(), request.nonce());
		if (profile.subject() == null || profile.subject().isBlank() || profile.subject().length() > 255) {
			throw new AuthException("OAUTH_SUBJECT_MISSING", "소셜 계정 식별자를 받지 못했습니다.", HttpStatus.BAD_GATEWAY);
		}
		return profile;
	}
}
