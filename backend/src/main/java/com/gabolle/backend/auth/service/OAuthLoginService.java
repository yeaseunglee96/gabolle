package com.gabolle.backend.auth.service;

import com.gabolle.backend.auth.api.OAuthLoginRequest;
import com.gabolle.backend.auth.domain.AuthProvider;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;

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

	public OAuthAccountService.OAuthAccountResult login(AuthProvider provider, OAuthLoginRequest request) {
		if (!request.ageGateAccepted()) {
			throw new AuthException("AGE_GATE_REQUIRED", "14세 이상 확인이 필요합니다.", HttpStatus.BAD_REQUEST);
		}
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
		return accountService.loginOrRegister(provider, profile, request.deviceId(), request.consents(),
				request.behaviorPersonalizationEnabled());
	}
}
