package com.gabolle.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gabolle.backend.auth.config.AuthProperties;
import com.gabolle.backend.auth.domain.AuthOneTimeToken;
import com.gabolle.backend.auth.domain.AuthSession;
import com.gabolle.backend.auth.domain.AuthTokenPurpose;
import com.gabolle.backend.auth.domain.LocalCredential;
import com.gabolle.backend.auth.repository.AuthOneTimeTokenRepository;
import com.gabolle.backend.auth.repository.AuthSessionRepository;
import com.gabolle.backend.auth.repository.AuthRefreshTokenRepository;
import com.gabolle.backend.auth.repository.LocalCredentialRepository;
import com.gabolle.backend.user.domain.AppUser;
import com.gabolle.backend.user.domain.PersonalizationMode;
import com.gabolle.backend.user.domain.UserStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

	@Mock private LocalCredentialRepository credentialRepository;
	@Mock private AuthOneTimeTokenRepository oneTimeTokenRepository;
	@Mock private AuthSessionRepository sessionRepository;
	@Mock private AuthRefreshTokenRepository refreshTokenRepository;
	@Mock private PasswordEncoder passwordEncoder;
	@Mock private EmailSender emailSender;

	private PasswordResetService service;
	private LocalCredential credential;
	private Instant now;

	@BeforeEach
	void setUp() {
		now = Instant.parse("2026-01-01T00:00:00Z");
		AppUser user = AppUser.register("여행자", "KO", now, "2026-01", PersonalizationMode.EXPLICIT_ONLY,
				UserStatus.ACTIVE);
		credential = LocalCredential.create(user, "traveler@example.com", "old-hash");
		service = new PasswordResetService(credentialRepository, oneTimeTokenRepository, sessionRepository,
				refreshTokenRepository,
				passwordEncoder, new SessionTokenGenerator(), emailSender, new AuthProperties(),
				Clock.fixed(now, ZoneOffset.UTC));
	}

	@Test
	void requestDoesNotRevealUnknownEmailAndIssuesForKnownEmail() {
		when(credentialRepository.findByEmailForUpdate("traveler@example.com")).thenReturn(Optional.of(credential));
		when(oneTimeTokenRepository.findTopByLocalCredentialAndPurposeOrderByCreatedAtDesc(
				credential, AuthTokenPurpose.PASSWORD_RESET)).thenReturn(Optional.empty());
		when(oneTimeTokenRepository.findAllByLocalCredentialAndPurposeAndConsumedAtIsNull(
				credential, AuthTokenPurpose.PASSWORD_RESET)).thenReturn(List.of());
		when(oneTimeTokenRepository.save(any(AuthOneTimeToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

		service.request("traveler@example.com");
		service.request("unknown@example.com");

		verify(emailSender).sendPasswordReset(any(), any());
	}

	@Test
	void confirmChangesPasswordConsumesTokenAndRevokesSessions() {
		String rawToken = "reset-token";
		SessionTokenGenerator tokenGenerator = new SessionTokenGenerator();
		AuthOneTimeToken token = AuthOneTimeToken.issue(credential, AuthTokenPurpose.PASSWORD_RESET,
				tokenGenerator.hash(rawToken), now.plusSeconds(60));
		when(oneTimeTokenRepository.findByTokenHashAndPurpose(tokenGenerator.hash(rawToken), AuthTokenPurpose.PASSWORD_RESET))
				.thenReturn(Optional.of(token));
		when(passwordEncoder.encode("NewRoute!2026")).thenReturn("new-hash");
		AuthSession session = AuthSession.issue(credential.getUser(), UUID.randomUUID(), "refresh-hash", "device-1",
				now.plusSeconds(3600));
		when(sessionRepository.findAllByUserUserIdAndRevokedAtIsNull(credential.getUser().getUserId()))
				.thenReturn(List.of(session));

		service.confirm(rawToken, "NewRoute!2026");

		assertThat(credential.getPasswordHash()).isEqualTo("new-hash");
		assertThat(token.getConsumedAt()).isEqualTo(now);
		assertThat(session.getRevokedAt()).isEqualTo(now);
	}

	// ── 비밀번호 재설정 링크 ────────────────────────────────────────────────

	@Test
	void resetLinkIsValidForThirtyMinutes() {
		when(credentialRepository.findByEmailForUpdate("traveler@example.com")).thenReturn(Optional.of(credential));
		when(oneTimeTokenRepository.findTopByLocalCredentialAndPurposeOrderByCreatedAtDesc(
				credential, AuthTokenPurpose.PASSWORD_RESET)).thenReturn(Optional.empty());
		when(oneTimeTokenRepository.findAllByLocalCredentialAndPurposeAndConsumedAtIsNull(
				credential, AuthTokenPurpose.PASSWORD_RESET)).thenReturn(List.of());
		ArgumentCaptor<AuthOneTimeToken> saved = ArgumentCaptor.forClass(AuthOneTimeToken.class);
		when(oneTimeTokenRepository.save(saved.capture())).thenAnswer(invocation -> invocation.getArgument(0));

		service.request("traveler@example.com");

		assertThat(saved.getValue().getExpiresAt()).isEqualTo(now.plus(Duration.ofMinutes(30)));
	}

	/**
	 * 링크가 실제로 착지하는 곳을 못 박는다. 프런트의 화면은 {@code /auth/password/reset}
	 * 이고 쿼리 {@code token} 을 읽는다 — 주소가 어긋나면 메일은 나가지만 링크는 아무 데도
	 * 닿지 않는다.
	 */
	@Test
	void resetMailLinkPointsAtTheFrontendScreenWithTheToken() {
		when(credentialRepository.findByEmailForUpdate("traveler@example.com")).thenReturn(Optional.of(credential));
		when(oneTimeTokenRepository.findTopByLocalCredentialAndPurposeOrderByCreatedAtDesc(
				credential, AuthTokenPurpose.PASSWORD_RESET)).thenReturn(Optional.empty());
		when(oneTimeTokenRepository.findAllByLocalCredentialAndPurposeAndConsumedAtIsNull(
				credential, AuthTokenPurpose.PASSWORD_RESET)).thenReturn(List.of());
		when(oneTimeTokenRepository.save(any(AuthOneTimeToken.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
		ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);

		service.request("traveler@example.com");

		verify(emailSender).sendPasswordReset(eq("traveler@example.com"), link.capture());
		assertThat(link.getValue()).startsWith(new AuthProperties().getPasswordResetBaseUrl() + "?token=");
		assertThat(link.getValue()).doesNotContain("localhost");
	}

	/** 1회용이다. 한 번 쓴 링크로 다시 바꿀 수 있으면 메일함을 본 사람이 언제든 계정을 가져간다. */
	@Test
	void consumedTokenCannotBeUsedAgain() {
		String rawToken = "reset-token";
		SessionTokenGenerator tokenGenerator = new SessionTokenGenerator();
		AuthOneTimeToken token = AuthOneTimeToken.issue(credential, AuthTokenPurpose.PASSWORD_RESET,
				tokenGenerator.hash(rawToken), now.plus(Duration.ofMinutes(30)));
		token.consume(now.minusSeconds(1));
		when(oneTimeTokenRepository.findByTokenHashAndPurpose(tokenGenerator.hash(rawToken),
				AuthTokenPurpose.PASSWORD_RESET)).thenReturn(Optional.of(token));

		assertThatThrownBy(() -> service.confirm(rawToken, "NewRoute!2026"))
				.isInstanceOf(AuthException.class)
				.hasMessageContaining("유효하지 않거나 만료된");
		verify(passwordEncoder, never()).encode(any());
	}

	@Test
	void expiredTokenIsRejected() {
		String rawToken = "reset-token";
		SessionTokenGenerator tokenGenerator = new SessionTokenGenerator();
		AuthOneTimeToken token = AuthOneTimeToken.issue(credential, AuthTokenPurpose.PASSWORD_RESET,
				tokenGenerator.hash(rawToken), now.minusSeconds(1));
		when(oneTimeTokenRepository.findByTokenHashAndPurpose(tokenGenerator.hash(rawToken),
				AuthTokenPurpose.PASSWORD_RESET)).thenReturn(Optional.of(token));

		assertThatThrownBy(() -> service.confirm(rawToken, "NewRoute!2026"))
				.isInstanceOf(AuthException.class);
		verify(passwordEncoder, never()).encode(any());
	}
}
