package com.gabolle.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import com.gabolle.backend.auth.domain.AuthIdentity;
import com.gabolle.backend.auth.domain.AuthProvider;
import com.gabolle.backend.auth.domain.LocalCredential;
import com.gabolle.backend.auth.repository.AuthIdentityRepository;
import com.gabolle.backend.auth.repository.LocalCredentialRepository;
import com.gabolle.backend.auth.service.AuthException;
import com.gabolle.backend.auth.service.LinkedIdentityService;
import com.gabolle.backend.auth.service.OAuthAccountService;
import com.gabolle.backend.auth.service.OAuthProviderClient;
import com.gabolle.backend.auth.support.AuthPostgresIntegrationTest;
import com.gabolle.backend.user.domain.AppUser;
import com.gabolle.backend.user.domain.PersonalizationMode;
import com.gabolle.backend.user.domain.UserStatus;
import com.gabolle.backend.user.repository.AppUserRepository;

/**
 * 연결된 소셜 계정을 보여주고 뗀다.
 *
 * <p>마지막 로그인 수단을 떼면 그 사람은 다시 못 들어오고 되돌릴 방법이 없다. 그래서
 * 뗄 수 있나 판정을 여기서 못으로 박는다. 메일 인증을 안 끝낸 비밀번호가 함정이다 —
 * {@code local_credential} 행은 있지만 그 비밀번호로는 로그인이 거부된다.
 *
 * <p>진짜 DB 가 필요한 이유는 {@code (provider, provider_subject)} 유일 제약이다. 뗀 연결을
 * 다시 붙이는 길을 안 열어 두면 그 제약에 걸리는데, 가짜 저장소에는 제약이 없어 초록이 난다.
 */
class LinkedIdentityIntegrationTest extends AuthPostgresIntegrationTest {

	@Autowired
	private LinkedIdentityService linkedIdentityService;

	@Autowired
	private OAuthAccountService oAuthAccountService;

	@Autowired
	private AuthIdentityRepository identityRepository;

	@Autowired
	private LocalCredentialRepository credentialRepository;

	@Autowired
	private AppUserRepository userRepository;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@Test
	@DisplayName("목록은 지금 붙어 있는 것만 보여준다 — 뗀 것은 안 나온다")
	void listShowsOnlyActiveLinks() {
		UUID userId = newUser();
		link(userId, AuthProvider.KAKAO, "kakao-1", "traveler@example.com");
		link(userId, AuthProvider.GOOGLE, "google-1", null);
		withPassword(userId, "traveler@example.com", true);

		this.transactionTemplate.executeWithoutResult(
				status -> this.linkedIdentityService.unlink(userId, AuthProvider.GOOGLE));

		LinkedIdentityService.LinkedIdentities identities = this.linkedIdentityService.list(userId);
		assertThat(identities.items()).extracting(LinkedIdentityService.LinkedIdentitySummary::provider)
				.containsExactly("KAKAO");
		assertThat(identities.canSignInWithPassword()).isTrue();
	}

	@Test
	@DisplayName("🔴 티켓 완료 기준 — 소셜 하나뿐이고 비밀번호가 없으면 못 뗀다")
	void cannotUnlinkTheOnlyWayIn() {
		UUID userId = newUser();
		link(userId, AuthProvider.KAKAO, "kakao-2", null);

		assertThat(this.linkedIdentityService.list(userId).items())
				.singleElement()
				.satisfies(item -> assertThat(item.canUnlink()).isFalse());

		assertThatThrownBy(() -> this.transactionTemplate.executeWithoutResult(
				status -> this.linkedIdentityService.unlink(userId, AuthProvider.KAKAO)))
				.isInstanceOfSatisfying(AuthException.class,
						exception -> assertThat(exception.getCode()).isEqualTo("LAST_SIGN_IN_METHOD"));

		// 거절했으면 아무것도 안 바뀌어야 한다.
		assertThat(this.linkedIdentityService.list(userId).items()).hasSize(1);
	}

	/**
	 * {@code local_credential} 행이 있다는 것만으로 다른 수단이 있다고 세면, 메일 인증을 안
	 * 끝낸 사람이 마지막 소셜을 떼고 다시 못 들어온다.
	 */
	@Test
	@DisplayName("🔴 메일 인증을 안 끝낸 비밀번호는 로그인 수단으로 안 센다")
	void unverifiedPasswordIsNotAWayIn() {
		UUID userId = newUser();
		link(userId, AuthProvider.KAKAO, "kakao-3", null);
		withPassword(userId, "unverified@example.com", false);

		LinkedIdentityService.LinkedIdentities identities = this.linkedIdentityService.list(userId);
		assertThat(identities.canSignInWithPassword()).isFalse();
		assertThat(identities.items()).singleElement()
				.satisfies(item -> assertThat(item.canUnlink()).isFalse());
	}

	@Test
	@DisplayName("소셜이 둘이면 하나는 뗄 수 있다 — 남는 길이 있다")
	void canUnlinkWhenAnotherRemains() {
		UUID userId = newUser();
		link(userId, AuthProvider.KAKAO, "kakao-4", null);
		link(userId, AuthProvider.NAVER, "naver-4", null);

		this.transactionTemplate.executeWithoutResult(
				status -> this.linkedIdentityService.unlink(userId, AuthProvider.NAVER));

		assertThat(this.linkedIdentityService.list(userId).items())
				.extracting(LinkedIdentityService.LinkedIdentitySummary::provider).containsExactly("KAKAO");
	}

	@Test
	@DisplayName("🔴 이미 안 붙어 있으면 성공이다 — 두 번 눌러도 실패로 보이지 않는다")
	void unlinkingWhatIsNotLinkedSucceeds() {
		UUID userId = newUser();
		link(userId, AuthProvider.KAKAO, "kakao-5", null);
		withPassword(userId, "traveler5@example.com", true);

		this.transactionTemplate.executeWithoutResult(
				status -> this.linkedIdentityService.unlink(userId, AuthProvider.NAVER));

		assertThat(this.linkedIdentityService.list(userId).items()).hasSize(1);
	}

	/**
	 * 끊긴 연결은 누구의 것도 아니다. 행은 남아 있으므로 다시 붙이는 길을 안 열면
	 * {@code (provider, provider_subject)} 유일 제약에 걸려 다시 붙일 수 없다.
	 */
	@Test
	@DisplayName("🔴 뗀 소셜 계정은 다시 붙일 수 있고, 다른 계정이 가져갈 수도 있다")
	void unlinkedIdentityCanBeClaimedAgain() {
		UUID first = newUser();
		UUID second = newUser();
		link(first, AuthProvider.GOOGLE, "google-shared", "shared@example.com");
		withPassword(first, "first@example.com", true);
		this.transactionTemplate.executeWithoutResult(
				status -> this.linkedIdentityService.unlink(first, AuthProvider.GOOGLE));

		OAuthAccountService.LinkedIdentity claimed = this.transactionTemplate.execute(
				status -> this.oAuthAccountService.linkAuthenticated(second, AuthProvider.GOOGLE,
						new OAuthProviderClient.OAuthUserProfile("google-shared", "shared@example.com", "여행자", "KO",
								Boolean.TRUE, null)));

		assertThat(claimed.alreadyLinked()).isFalse();
		assertThat(this.linkedIdentityService.list(second).items())
				.extracting(LinkedIdentityService.LinkedIdentitySummary::provider).containsExactly("GOOGLE");
		assertThat(this.linkedIdentityService.list(first).items()).isEmpty();
	}

	// ── 밑준비 ────────────────────────────────────────────────────────────────

	private UUID newUser() {
		return this.transactionTemplate.execute(status -> this.userRepository.save(
				AppUser.register("여행자", "KO", Instant.now(), "2026-01", PersonalizationMode.EXPLICIT_ONLY,
						UserStatus.ACTIVE)).getUserId());
	}

	private void link(UUID userId, AuthProvider provider, String subject, String email) {
		this.transactionTemplate.executeWithoutResult(status -> {
			AppUser user = this.userRepository.findById(userId).orElseThrow();
			this.identityRepository.save(AuthIdentity.link(user, provider, subject, email));
		});
	}

	private void withPassword(UUID userId, String email, boolean emailVerified) {
		this.transactionTemplate.executeWithoutResult(status -> {
			AppUser user = this.userRepository.findById(userId).orElseThrow();
			LocalCredential credential = LocalCredential.create(user, email, "hash-not-used-here");
			if (emailVerified) {
				credential.markEmailVerified(Instant.now());
			}
			this.credentialRepository.save(credential);
		});
	}
}
