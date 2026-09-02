package com.gabolle.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gabolle.backend.auth.config.AuthProperties;
import com.gabolle.backend.auth.domain.AuthRefreshToken;
import com.gabolle.backend.auth.domain.AuthSession;
import com.gabolle.backend.auth.repository.AuthIdentityRepository;
import com.gabolle.backend.auth.repository.AuthRefreshTokenRepository;
import com.gabolle.backend.auth.repository.AuthSessionRepository;
import com.gabolle.backend.auth.repository.LocalCredentialRepository;
import com.gabolle.backend.user.domain.AppUser;
import com.gabolle.backend.user.domain.PersonalizationMode;
import com.gabolle.backend.user.domain.UserStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthTokenServiceTest {

	@Mock private AccessTokenIssuer accessTokenIssuer;
	@Mock private AuthSessionRepository sessionRepository;
	@Mock private AuthRefreshTokenRepository refreshTokenRepository;
	@Mock private LocalCredentialRepository credentialRepository;
	@Mock private AuthIdentityRepository identityRepository;

	private AuthTokenService service;
	private SessionTokenGenerator tokenGenerator;
	private AppUser user;
	private Instant now;

	@BeforeEach
	void setUp() {
		now = Instant.parse("2026-01-01T00:00:00Z");
		tokenGenerator = new SessionTokenGenerator();
		user = AppUser.register("여행자", "KO", now, "2026-01", PersonalizationMode.EXPLICIT_ONLY,
				UserStatus.ACTIVE);
		service = new AuthTokenService(accessTokenIssuer, new AuthProperties(), sessionRepository,
				refreshTokenRepository, credentialRepository, identityRepository, tokenGenerator,
				Clock.fixed(now, ZoneOffset.UTC));
	}

	@Test
	void rotatesRefreshTokenAndConsumesPresentedToken() {
		String rawToken = "current-refresh-token";
		String currentHash = tokenGenerator.hash(rawToken);
		AuthSession session = AuthSession.issue(user, UUID.randomUUID(), currentHash, "device-1", now.plusSeconds(3600));
		AuthRefreshToken history = AuthRefreshToken.issue(session, currentHash, now.plusSeconds(3600));
		when(refreshTokenRepository.findByTokenHash(currentHash)).thenReturn(Optional.of(history));
		when(sessionRepository.save(any(AuthSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(refreshTokenRepository.save(any(AuthRefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(accessTokenIssuer.issue(any(AppUser.class), org.mockito.ArgumentMatchers.eq(now),
				org.mockito.ArgumentMatchers.nullable(UUID.class))).thenReturn(
				new AccessTokenIssuer.IssuedAccessToken("access-token", now.plusSeconds(1800)));
		when(credentialRepository.findByUserUserId(user.getUserId())).thenReturn(Optional.empty());
		when(identityRepository.findAllByUserUserId(user.getUserId())).thenReturn(List.of());

		AuthTokenService.IssuedTokens result = service.refresh(rawToken, "device-1");

		assertThat(result.refreshToken()).isNotEqualTo(rawToken);
		assertThat(history.getUsedAt()).isEqualTo(now);
		assertThat(session.getRefreshTokenHash()).isNotEqualTo(currentHash);
	}

	@Test
	void revokesTokenFamilyWhenConsumedRefreshTokenIsReused() {
		String rawToken = "used-refresh-token";
		String tokenHash = tokenGenerator.hash(rawToken);
		UUID familyId = UUID.randomUUID();
		AuthSession session = AuthSession.issue(user, familyId, "new-current-hash", "device-1", now.plusSeconds(3600));
		AuthRefreshToken reused = AuthRefreshToken.issue(session, tokenHash, now.plusSeconds(3600));
		reused.consume(now.minusSeconds(10));
		when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(reused));
		when(refreshTokenRepository.findAllBySessionTokenFamilyIdAndRevokedAtIsNull(familyId))
				.thenReturn(List.of(reused));
		when(sessionRepository.findAllByTokenFamilyIdAndRevokedAtIsNull(familyId)).thenReturn(List.of(session));

		assertThatThrownBy(() -> service.refresh(rawToken, "device-1"))
				.isInstanceOfSatisfying(AuthException.class,
						exception -> assertThat(exception.getCode()).isEqualTo("REFRESH_TOKEN_REUSED"));

		assertThat(reused.getRevokedAt()).isEqualTo(now);
		assertThat(session.getRevokedAt()).isEqualTo(now);
		verify(accessTokenIssuer, never()).issue(any(), any(), any());
	}

	@Test
	void rejectsRefreshFromAnotherDevice() {
		String rawToken = "device-bound-refresh-token";
		String tokenHash = tokenGenerator.hash(rawToken);
		AuthSession session = AuthSession.issue(user, UUID.randomUUID(), tokenHash, "device-1", now.plusSeconds(3600));
		AuthRefreshToken refreshToken = AuthRefreshToken.issue(session, tokenHash, now.plusSeconds(3600));
		when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(refreshToken));

		assertThatThrownBy(() -> service.refresh(rawToken, "device-2"))
				.isInstanceOf(AuthException.class)
				.hasMessageContaining("기기");
		verify(accessTokenIssuer, never()).issue(any(), any(), any());
	}
}
