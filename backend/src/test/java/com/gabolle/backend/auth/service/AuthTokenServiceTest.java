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

	private AuthProperties properties;
	private SessionTokenGenerator tokenGenerator;
	private AppUser user;
	private Instant now;

	@BeforeEach
	void setUp() {
		now = Instant.parse("2026-01-01T00:00:00Z");
		tokenGenerator = new SessionTokenGenerator();
		user = AppUser.register("여행자", "KO", now, "2026-01", PersonalizationMode.EXPLICIT_ONLY,
				UserStatus.ACTIVE);
		this.properties = new AuthProperties();
		// 관측(SecurityEventLogger)은 null 로 둔다 — 이 테스트는 토큰 회전 동작만 재고,
		// 로그가 남는지는 SecurityEventLogger 쪽 테스트가 본다
		service = new AuthTokenService(accessTokenIssuer, this.properties, sessionRepository,
				refreshTokenRepository, credentialRepository, identityRepository, tokenGenerator,
				null, Clock.fixed(now, ZoneOffset.UTC));
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

	/**
	 * 손 놓고 2시간이 지나면 풀린다. 재는 것은 둘이다 — 설정 없이 띄웠을 때의 기본값이
	 * 2시간인 것과, 갱신할 때마다 만료가 그 시점 기준으로 다시 밀리는 것. 뒤의 것이 없으면
	 * 이 값은 손 놓은 시간이 아니라 로그인한 뒤 총 시간이 되어 쓰는 도중에 튕긴다.
	 */
	@Test
	void refreshPushesSessionExpiryToTwoHoursFromNow() {
		assertThat(new AuthProperties().getRefreshTokenTtl()).isEqualTo(java.time.Duration.ofHours(2));

		String rawToken = "current-refresh-token";
		String currentHash = tokenGenerator.hash(rawToken);
		// 만료가 5분밖에 안 남은 세션 — 갱신하면 2시간으로 다시 밀려야 한다
		AuthSession session = AuthSession.issue(user, UUID.randomUUID(), currentHash, "device-1", now.plusSeconds(300));
		AuthRefreshToken history = AuthRefreshToken.issue(session, currentHash, now.plusSeconds(300));
		when(refreshTokenRepository.findByTokenHash(currentHash)).thenReturn(Optional.of(history));
		when(sessionRepository.save(any(AuthSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(refreshTokenRepository.save(any(AuthRefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(accessTokenIssuer.issue(any(AppUser.class), org.mockito.ArgumentMatchers.eq(now),
				org.mockito.ArgumentMatchers.nullable(UUID.class))).thenReturn(
				new AccessTokenIssuer.IssuedAccessToken("access-token", now.plusSeconds(1800)));
		when(credentialRepository.findByUserUserId(user.getUserId())).thenReturn(Optional.empty());
		when(identityRepository.findAllByUserUserId(user.getUserId())).thenReturn(List.of());

		service.refresh(rawToken, "device-1");

		assertThat(session.getExpiresAt()).isEqualTo(now.plus(java.time.Duration.ofHours(2)));
	}

	/**
	 * 소비된 표를 다시 쓰면 세션 계열을 폐기한다. 유예(기본 30초) 안의 재사용은 도난이
	 * 아니라 경쟁으로 처리되므로, 도난을 재려면 시각을 유예 밖으로 잡아야 한다.
	 */
	@Test
	void revokesTokenFamilyWhenConsumedRefreshTokenIsReused() {
		String rawToken = "used-refresh-token";
		String tokenHash = tokenGenerator.hash(rawToken);
		UUID familyId = UUID.randomUUID();
		AuthSession session = AuthSession.issue(user, familyId, "new-current-hash", "device-1", now.plusSeconds(3600));
		AuthRefreshToken reused = AuthRefreshToken.issue(session, tokenHash, now.plusSeconds(3600));
		reused.consume(now.minusSeconds(120));
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

	/**
	 * 거의 동시에 두 번 갱신하면 둘 다 성공하고 세션이 살아 있다.
	 *
	 * <p>접속 토큰이 만료된 상태에서 탭 둘이 거의 동시에 갱신하면 하나만 성공하고 나머지는
	 * 같은 표를 낸다. 그것을 도난으로 보고 세션 계열을 폐기하면 아무도 잘못하지 않았는데
	 * 로그아웃된다.
	 */
	@Test
	void allowsNearSimultaneousRefreshWithoutRevokingTheSession() {
		String rawToken = "raced-refresh-token";
		String tokenHash = tokenGenerator.hash(rawToken);
		UUID familyId = UUID.randomUUID();
		AuthSession session = AuthSession.issue(user, familyId, "hash-from-the-winner", "device-1",
				now.plusSeconds(3600));
		AuthRefreshToken raced = AuthRefreshToken.issue(session, tokenHash, now.plusSeconds(3600));
		// 5초 전에 이미 쓰였다 — 기본 유예(30초) 안이다
		raced.consume(now.minusSeconds(5));
		when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(raced));
		when(sessionRepository.save(any(AuthSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(refreshTokenRepository.save(any(AuthRefreshToken.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
		when(accessTokenIssuer.issue(any(AppUser.class), org.mockito.ArgumentMatchers.eq(now),
				org.mockito.ArgumentMatchers.nullable(UUID.class))).thenReturn(
				new AccessTokenIssuer.IssuedAccessToken("access-token", now.plusSeconds(1800)));
		when(credentialRepository.findByUserUserId(user.getUserId())).thenReturn(Optional.empty());
		when(identityRepository.findAllByUserUserId(user.getUserId())).thenReturn(List.of());

		AuthTokenService.IssuedTokens result = service.refresh(rawToken, "device-1");

		assertThat(result.refreshToken()).isNotBlank().isNotEqualTo(rawToken);
		// 세션이 죽지 않았다
		assertThat(session.getRevokedAt()).isNull();
		assertThat(raced.getRevokedAt()).isNull();
	}

	@Test
	void graceOfZeroKeepsTheOldImmediateRevocation() {
		// 유예를 0 으로 두면 예전 동작과 같아진다 — 도난 사고가 생기면 이렇게 되돌린다
		this.properties.setRefreshReuseGrace(java.time.Duration.ZERO);

		String rawToken = "raced-but-no-grace";
		String tokenHash = tokenGenerator.hash(rawToken);
		UUID familyId = UUID.randomUUID();
		AuthSession session = AuthSession.issue(user, familyId, "hash-from-the-winner", "device-1",
				now.plusSeconds(3600));
		AuthRefreshToken raced = AuthRefreshToken.issue(session, tokenHash, now.plusSeconds(3600));
		raced.consume(now.minusSeconds(1));
		when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(raced));
		when(refreshTokenRepository.findAllBySessionTokenFamilyIdAndRevokedAtIsNull(familyId))
				.thenReturn(List.of(raced));
		when(sessionRepository.findAllByTokenFamilyIdAndRevokedAtIsNull(familyId)).thenReturn(List.of(session));

		assertThatThrownBy(() -> service.refresh(rawToken, "device-1"))
				.isInstanceOfSatisfying(AuthException.class,
						exception -> assertThat(exception.getCode()).isEqualTo("REFRESH_TOKEN_REUSED"));
		assertThat(session.getRevokedAt()).isEqualTo(now);
	}

	@Test
	void graceDoesNotResurrectARevokedSession() {
		// 유예는 "경쟁을 허용" 하는 것이고 "죽은 세션을 살리는" 것이 아니다.
		// 이미 폐기된 세션의 표가 유예 안에 와도 거절한다
		String rawToken = "raced-on-a-dead-session";
		String tokenHash = tokenGenerator.hash(rawToken);
		AuthSession session = AuthSession.issue(user, UUID.randomUUID(), "hash", "device-1", now.plusSeconds(3600));
		session.revoke(now.minusSeconds(60));
		AuthRefreshToken raced = AuthRefreshToken.issue(session, tokenHash, now.plusSeconds(3600));
		raced.consume(now.minusSeconds(5));
		when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(raced));

		assertThatThrownBy(() -> service.refresh(rawToken, "device-1"))
				.isInstanceOfSatisfying(AuthException.class,
						exception -> assertThat(exception.getCode()).isEqualTo("INVALID_REFRESH_TOKEN"));
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
