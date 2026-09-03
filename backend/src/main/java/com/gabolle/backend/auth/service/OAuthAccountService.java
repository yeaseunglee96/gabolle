package com.gabolle.backend.auth.service;

import com.gabolle.backend.auth.config.AuthProperties;
import com.gabolle.backend.auth.domain.AuthIdentity;
import com.gabolle.backend.auth.domain.AuthProvider;
import com.gabolle.backend.auth.repository.AuthIdentityRepository;
import com.gabolle.backend.auth.repository.LocalCredentialRepository;
import com.gabolle.backend.user.domain.AppUser;
import com.gabolle.backend.user.domain.ConsentStatus;
import com.gabolle.backend.user.domain.ConsentType;
import com.gabolle.backend.user.domain.PersonalizationMode;
import com.gabolle.backend.user.domain.UserStatus;
import com.gabolle.backend.user.repository.AppUserRepository;
import com.gabolle.backend.user.domain.UserConsent;
import com.gabolle.backend.user.repository.UserConsentRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile({"db", "dev"})
public class OAuthAccountService {

	private final AuthIdentityRepository identityRepository;
	private final LocalCredentialRepository credentialRepository;
	private final AppUserRepository userRepository;
	private final UserConsentRepository consentRepository;
	private final AuthTokenService tokenService;
	private final AuthProperties properties;
	private final ConsentPolicy consentPolicy;
	private final Clock clock;

	@Autowired
	public OAuthAccountService(AuthIdentityRepository identityRepository, LocalCredentialRepository credentialRepository,
			AppUserRepository userRepository, UserConsentRepository consentRepository, AuthTokenService tokenService,
			AuthProperties properties, ConsentPolicy consentPolicy) {
		this(identityRepository, credentialRepository, userRepository, consentRepository, tokenService, properties,
				consentPolicy, Clock.systemUTC());
	}

	OAuthAccountService(AuthIdentityRepository identityRepository, LocalCredentialRepository credentialRepository,
			AppUserRepository userRepository, UserConsentRepository consentRepository, AuthTokenService tokenService,
			AuthProperties properties, ConsentPolicy consentPolicy, Clock clock) {
		this.identityRepository = identityRepository;
		this.credentialRepository = credentialRepository;
		this.userRepository = userRepository;
		this.consentRepository = consentRepository;
		this.tokenService = tokenService;
		this.properties = properties;
		this.consentPolicy = consentPolicy;
		this.clock = clock;
	}

	@Transactional
	public OAuthAccountResult loginOrRegister(AuthProvider provider, OAuthProviderClient.OAuthUserProfile profile,
			String deviceId, Map<String, Boolean> rawConsents, boolean behaviorPersonalizationEnabled) {
		AuthIdentity identity = identityRepository.findByProviderAndProviderSubject(provider, profile.subject()).orElse(null);
		if (identity == null) {
			if (profile.email() == null || profile.email().isBlank()) {
				throw new AuthException("PROVIDER_EMAIL_REQUIRED", "소셜 계정 이메일이 필요합니다.",
						HttpStatus.UNPROCESSABLE_CONTENT);
			}
			String email = normalizeEmail(profile.email());
			if (email.length() > 254) {
				throw new AuthException("PROVIDER_EMAIL_INVALID", "소셜 계정 이메일 형식이 올바르지 않습니다.",
						HttpStatus.UNPROCESSABLE_CONTENT);
			}
			if (credentialRepository.findByEmail(email).isPresent()) {
				throw new AuthException("OAUTH_ACCOUNT_LINK_REQUIRED", "이미 가입된 이메일입니다. 기존 계정에 로그인한 뒤 소셜 계정을 연결해 주세요.",
						HttpStatus.CONFLICT);
			}
			Map<ConsentType, Boolean> consents = consentPolicy.validate(rawConsents, behaviorPersonalizationEnabled);
			Instant now = clock.instant();
			AppUser user = userRepository.save(AppUser.register(
					normalizeDisplayName(profile.displayName()),
					LanguageNormalizer.normalize(profile.language()), now,
					properties.getAgeGatePolicyVersion(),
					behaviorPersonalizationEnabled ? PersonalizationMode.BEHAVIOR_ENABLED : PersonalizationMode.EXPLICIT_ONLY,
					UserStatus.ACTIVE));
			consents.forEach((type, granted) -> consentRepository.save(UserConsent.decide(user, type,
					Boolean.TRUE.equals(granted) ? ConsentStatus.GRANTED : ConsentStatus.REVOKED,
					properties.getConsentPolicyVersion())));
			identity = identityRepository.save(AuthIdentity.link(user, provider, profile.subject(), email));
		} else {
			if (!identity.isActive()) {
				throw new AuthException("OAUTH_IDENTITY_UNLINKED", "연결이 해제된 소셜 계정입니다.", HttpStatus.CONFLICT);
			}
		}
		if (identity.getUser().getStatus() != UserStatus.ACTIVE) {
			throw new AuthException("ACCOUNT_UNAVAILABLE", "사용할 수 없는 계정입니다.", HttpStatus.FORBIDDEN);
		}
		String email = profile.email() == null || profile.email().isBlank()
				? identity.getProviderEmail() : normalizeEmail(profile.email());
		AuthTokenService.IssuedTokens tokens = tokenService.issue(identity.getUser(), deviceId, email);
		return new OAuthAccountResult(tokens, identity.getUser(), email);
	}

	private String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

	private String normalizeDisplayName(String displayName) {
		String value = displayName == null || displayName.isBlank() ? "GABOLLE 사용자" : displayName.trim();
		return value.codePoints().limit(50)
				.collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
				.toString();
	}

	public record OAuthAccountResult(AuthTokenService.IssuedTokens tokens, AppUser user, String email) {
	}
}
