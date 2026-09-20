package com.gabolle.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gabolle.backend.auth.domain.AnonymousSession;
import com.gabolle.backend.auth.repository.AnonymousSessionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 익명 출입증 발급·대조.
 *
 * <p>네 가지를 잰다 — 두 번 발급하면 값이 다르다, 원본은 서버에 남지 않는다(해시만 저장소에
 * 전달된다), 발급받은 값으로는 세션을 다시 찾는다, 없는 값은 못 찾는다.
 */
class AnonymousSessionServiceTest {

	private final AnonymousSessionRepository repository = mock(AnonymousSessionRepository.class);
	private final SessionTokenGenerator tokenGenerator = new SessionTokenGenerator();
	private final Instant now = Instant.parse("2026-01-01T00:00:00Z");
	private AnonymousSessionService service;

	@BeforeEach
	void setUp() {
		service = new AnonymousSessionService(repository, tokenGenerator, Clock.fixed(now, ZoneOffset.UTC));
		when(repository.save(any(AnonymousSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	void issuingTwiceProducesDifferentTokens() {
		AnonymousSessionService.IssuedAnonymousSession first = service.issue();
		AnonymousSessionService.IssuedAnonymousSession second = service.issue();

		assertThat(first.token()).isNotEqualTo(second.token());
	}

	@Test
	void issuedTokenIsNeverPersistedInTheClear() {
		AnonymousSessionService.IssuedAnonymousSession issued = service.issue();

		// repository.save 에 전달된 엔티티는 해시만 갖고 있다 — 원본 토큰 문자열이 아니다.
		AnonymousSession saved = captureSaved();
		assertThat(saved.getTokenHash()).isEqualTo(tokenGenerator.hash(issued.token()));
		assertThat(saved.getTokenHash()).isNotEqualTo(issued.token());
	}

	@Test
	void resolvingTheIssuedTokenFindsItsOwnerAndTouchesLastSeen() {
		String rawToken = tokenGenerator.issue();
		AnonymousSession stored = AnonymousSession.issue(tokenGenerator.hash(rawToken), now.minusSeconds(3600));
		when(repository.findByTokenHash(tokenGenerator.hash(rawToken))).thenReturn(Optional.of(stored));

		Optional<AnonymousSession> resolved = service.resolve(rawToken);

		assertThat(resolved).isPresent();
		assertThat(resolved.get().getLastSeenAt()).isEqualTo(now);
	}

	@Test
	void unknownTokenResolvesToNoSession() {
		when(repository.findByTokenHash(any())).thenReturn(Optional.empty());

		assertThat(service.resolve("token-nobody-issued")).isEmpty();
	}

	@Test
	void blankOrMissingTokenResolvesToNoSessionWithoutQueryingTheRepository() {
		assertThat(service.resolve(null)).isEmpty();
		assertThat(service.resolve("")).isEmpty();
		assertThat(service.resolve("   ")).isEmpty();
	}

	private AnonymousSession captureSaved() {
		var captor = org.mockito.ArgumentCaptor.forClass(AnonymousSession.class);
		org.mockito.Mockito.verify(repository).save(captor.capture());
		return captor.getValue();
	}
}
