package com.gabolle.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.auth.domain.AuthIdentity;
import com.gabolle.backend.auth.domain.LocalCredential;
import com.gabolle.backend.auth.repository.AuthIdentityRepository;
import com.gabolle.backend.auth.repository.LocalCredentialRepository;

/**
 * 사람에게 닿는 주소를 고르는 규칙. DB 가 필요 없는 판정이라 저장소를 대역으로 둔다.
 *
 * <p>규칙이 틀리면 알림이 아무에게도 안 가거나 남에게 간다. 뒤쪽을 재는 것이
 * {@link #movedAwayKakaoAddressIsNotUsed()} 다.
 */
class NotificationEmailResolverTest {

	private final LocalCredentialRepository credentials = mock(LocalCredentialRepository.class);

	private final AuthIdentityRepository identities = mock(AuthIdentityRepository.class);

	private final NotificationEmailResolver resolver = new NotificationEmailResolver(credentials, identities);

	@Test
	@DisplayName("비밀번호 계정의 주소를 먼저 쓴다 — 그쪽은 사용자가 실제로 받아 본 것이 증명돼 있다")
	void localCredentialWins() {
		UUID userId = UUID.randomUUID();
		LocalCredential credential = credentialWith("me@example.com");
		when(credentials.findByUserUserId(userId)).thenReturn(Optional.of(credential));

		assertThat(resolver.resolve(userId)).contains("me@example.com");
	}

	@Test
	@DisplayName("비밀번호 계정이 없으면 소셜이 준 주소를 쓴다")
	void fallsBackToProviderEmail() {
		UUID userId = UUID.randomUUID();
		when(credentials.findByUserUserId(userId)).thenReturn(Optional.empty());
		AuthIdentity social = identity("social@example.com", null, null, Instant.now());
		when(identities.findAllByUserUserId(userId)).thenReturn(List.of(social));

		assertThat(resolver.resolve(userId)).contains("social@example.com");
	}

	@Test
	@DisplayName("🔴 유효하지 않다고 표시된 주소는 안 쓴다 — 그 주소는 남에게 옮겨간 것이다")
	void movedAwayKakaoAddressIsNotUsed() {
		UUID userId = UUID.randomUUID();
		when(credentials.findByUserUserId(userId)).thenReturn(Optional.empty());
		AuthIdentity moved = identity("moved@example.com", null, false, Instant.now());
		when(identities.findAllByUserUserId(userId)).thenReturn(List.of(moved));

		assertThat(resolver.resolve(userId)).as("옮겨간 주소로 알림을 보내면 남의 우편함에 도착한다").isEmpty();
	}

	@Test
	@DisplayName("모름(null)은 배제하지 않는다 — 가입 티켓으로 만든 신원은 두 값이 비어서 시작한다")
	void unknownValidityIsStillUsed() {
		UUID userId = UUID.randomUUID();
		when(credentials.findByUserUserId(userId)).thenReturn(Optional.empty());
		AuthIdentity fresh = identity("fresh@example.com", null, null, Instant.now());
		when(identities.findAllByUserUserId(userId)).thenReturn(List.of(fresh));

		assertThat(resolver.resolve(userId)).contains("fresh@example.com");
	}

	@Test
	@DisplayName("연결이 끊긴 신원의 주소는 안 쓴다")
	void unlinkedIdentityIsIgnored() {
		UUID userId = UUID.randomUUID();
		when(credentials.findByUserUserId(userId)).thenReturn(Optional.empty());
		AuthIdentity unlinked = identity("old@example.com", Instant.now(), null, Instant.now());
		when(identities.findAllByUserUserId(userId)).thenReturn(List.of(unlinked));

		assertThat(resolver.resolve(userId)).isEmpty();
	}

	@Test
	@DisplayName("여럿이면 가장 최근에 연결한 것을 쓴다")
	void mostRecentlyLinkedWins() {
		UUID userId = UUID.randomUUID();
		Instant older = Instant.parse("2026-09-01T00:00:00Z");
		Instant newer = Instant.parse("2026-09-10T00:00:00Z");
		when(credentials.findByUserUserId(userId)).thenReturn(Optional.empty());
		AuthIdentity oldOne = identity("old@example.com", null, null, older);
		AuthIdentity newOne = identity("new@example.com", null, null, newer);
		when(identities.findAllByUserUserId(userId)).thenReturn(List.of(oldOne, newOne));

		assertThat(resolver.resolve(userId)).contains("new@example.com");
	}

	@Test
	@DisplayName("🔴 주소가 하나도 없으면 비어 있다 — 애플·기본 동의 카카오가 그렇다")
	void noAddressAtAll() {
		UUID userId = UUID.randomUUID();
		when(credentials.findByUserUserId(userId)).thenReturn(Optional.empty());
		AuthIdentity noEmail = identity(null, null, null, Instant.now());
		when(identities.findAllByUserUserId(userId)).thenReturn(List.of(noEmail));

		assertThat(resolver.resolve(userId)).isEmpty();
	}

	@Test
	@DisplayName("사용자 식별자가 없으면 저장소를 찾지도 않는다")
	void nullUserIdShortCircuits() {
		assertThat(resolver.resolve(null)).isEmpty();
	}

	/**
	 * 이 헬퍼를 {@code when(...)} 안에서 부르면 안 된다. Mockito 는 끝나지 않은 스터빙 안에서
	 * 다른 대역을 스터빙하는 것을 거부한다. 지역 변수로 먼저 만들고 그 변수를 넘긴다.
	 */
	private static LocalCredential credentialWith(String email) {
		LocalCredential credential = mock(LocalCredential.class);
		when(credential.getEmail()).thenReturn(email);
		return credential;
	}

	/**
	 * {@link AuthIdentity} 는 생성자가 비공개이고 {@code emailValid} 를 직접 넣는 창구가 없어
	 * (로그인 응답으로만 채워진다) 대역으로 만든다. 이 검사가 재는 것은 판정 규칙이지 그 클래스의
	 * 저장 동작이 아니다.
	 */
	private static AuthIdentity identity(String email, Instant unlinkedAt, Boolean emailValid, Instant linkedAt) {
		AuthIdentity identity = mock(AuthIdentity.class);
		when(identity.getProviderEmail()).thenReturn(email);
		when(identity.getUnlinkedAt()).thenReturn(unlinkedAt);
		when(identity.getEmailValid()).thenReturn(emailValid);
		when(identity.getLinkedAt()).thenReturn(linkedAt);
		return identity;
	}
}
