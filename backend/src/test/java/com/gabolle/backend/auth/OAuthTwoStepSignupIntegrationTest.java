package com.gabolle.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.gabolle.backend.auth.domain.AuthProvider;
import com.gabolle.backend.auth.domain.LocalCredential;
import com.gabolle.backend.auth.repository.AuthIdentityRepository;
import com.gabolle.backend.auth.repository.LocalCredentialRepository;
import com.gabolle.backend.auth.service.AuthException;
import com.gabolle.backend.auth.service.OAuthAccountService;
import com.gabolle.backend.auth.api.OAuthLoginResponse;
import com.gabolle.backend.auth.service.OAuthProviderClient;
import com.gabolle.backend.auth.support.AuthPostgresIntegrationTest;
import com.gabolle.backend.user.domain.AppUser;
import com.gabolle.backend.user.domain.PersonalizationMode;
import com.gabolle.backend.user.domain.UserStatus;
import com.gabolle.backend.user.repository.AppUserRepository;

/**
 * S15P21E201-689 · -690 — 소셜 인증 → 회원가입 / 기존 계정 연결이 실제 PostgreSQL 에서 도는지 본다.
 *
 * <p>provider 왕복(코드 교환)은 여기 범위가 아니다. 그 앞단은 {@code OAuthLoginService} 가 하고 provider 별 클라이언트
 * 테스트가 따로 있다. 이 테스트가 보는 것은 그 뒤 — 티켓이 표에 남는가, 한 번만 쓰이는가, 만료가 지켜지는가, 비밀번호를
 * 확인하고 신원을 붙이는가, 그리고 <b>계정이 언제 생기고 언제 안 생기는가</b>다.
 */
class OAuthTwoStepSignupIntegrationTest extends AuthPostgresIntegrationTest {

	@Autowired private OAuthAccountService accountService;
	@Autowired private AuthIdentityRepository identityRepository;
	@Autowired private LocalCredentialRepository credentialRepository;
	@Autowired private AppUserRepository userRepository;
	@Autowired private PasswordEncoder passwordEncoder;
	@Autowired private JdbcTemplate jdbc;

	private String subject;
	private String email;

	@BeforeEach
	void freshIdentity() {
		// 같은 DB 를 여러 테스트가 쓰므로 신원·이메일은 매번 새로 만든다(UNIQUE 충돌 방지).
		this.subject = "google-" + UUID.randomUUID();
		this.email = "oauth-" + UUID.randomUUID() + "@example.com";
	}

	private int ticketRows(String kind) {
		Integer n = jdbc.queryForObject(
				"SELECT count(*) FROM oauth_signup_ticket WHERE provider_subject = ? AND kind = ?", Integer.class,
				subject, kind);
		return n == null ? 0 : n;
	}

	private OAuthProviderClient.OAuthUserProfile profile() {
		return new OAuthProviderClient.OAuthUserProfile(subject, email, "여행자", "KO", null, null);
	}

	@Test
	@DisplayName("🔴 처음 보는 소셜 계정은 계정이 생기지 않고 가입 티켓만 남는다 — 그 티켓으로 가입하면 계정·신원이 함께 생긴다")
	void signupTicketThenAccount() {
		long usersBefore = userRepository.count();

		OAuthAccountService.Outcome outcome = accountService.authenticate(AuthProvider.GOOGLE, profile(), "device-1",
				null, false, false);

		assertThat(outcome).isInstanceOf(OAuthAccountService.SignupRequired.class);
		OAuthAccountService.SignupRequired signup = (OAuthAccountService.SignupRequired) outcome;
		assertThat(signup.signupTicket()).hasSize(43);
		assertThat(signup.email()).isEqualTo(email);
		assertThat(signup.ticketExpiresAt()).isAfter(Instant.now());
		assertThat(userRepository.count()).isEqualTo(usersBefore);
		assertThat(identityRepository.findByProviderAndProviderSubject(AuthProvider.GOOGLE, subject)).isEmpty();
		assertThat(ticketRows("SIGNUP")).isEqualTo(1);

		// 🔴 표에 원문이 없다 — 해시만 남는다.
		Integer rawInTable = jdbc.queryForObject(
				"SELECT count(*) FROM oauth_signup_ticket WHERE ticket_hash = ?", Integer.class, signup.signupTicket());
		assertThat(rawInTable).isZero();

		OAuthAccountService.LoggedIn result = (OAuthAccountService.LoggedIn) accountService.completeSignup(
				signup.signupTicket(), "내가 고친 이름", "EN",
				Map.of("TERMS_OF_SERVICE", true, "PRIVACY_POLICY", true), false, "device-1");

		assertThat(result.tokens().accessToken()).isNotBlank();
		assertThat(result.user().getDisplayName()).isEqualTo("내가 고친 이름");
		assertThat(result.user().getLanguage()).isEqualTo("EN");
		assertThat(identityRepository.findByProviderAndProviderSubject(AuthProvider.GOOGLE, subject)).isPresent();
		assertThat(userRepository.count()).isEqualTo(usersBefore + 1);
	}

	@Test
	@DisplayName("🔴 같은 가입 티켓을 두 번 쓰면 두 번째는 거절되고 계정이 하나만 남는다")
	void signupTicketIsSingleUse() {
		OAuthAccountService.SignupRequired signup = (OAuthAccountService.SignupRequired) accountService
				.authenticate(AuthProvider.GOOGLE, profile(), "device-1", null, false, false);
		accountService.completeSignup(signup.signupTicket(), null, null,
				Map.of("TERMS_OF_SERVICE", true, "PRIVACY_POLICY", true), false, "device-1");
		long usersAfterFirst = userRepository.count();

		assertThatThrownBy(() -> accountService.completeSignup(signup.signupTicket(), null, null,
				Map.of("TERMS_OF_SERVICE", true, "PRIVACY_POLICY", true), false, "device-1"))
				.isInstanceOf(AuthException.class)
				.hasFieldOrPropertyWithValue("code", "OAUTH_TICKET_INVALID");
		assertThat(userRepository.count()).isEqualTo(usersAfterFirst);
	}

	@Test
	@DisplayName("만료된 티켓과 없는 티켓은 같은 응답으로 거절된다")
	void expiredAndUnknownTicketsLookTheSame() {
		OAuthAccountService.SignupRequired signup = (OAuthAccountService.SignupRequired) accountService
				.authenticate(AuthProvider.GOOGLE, profile(), "device-1", null, false, false);
		// CHECK (expires_at > created_at) 를 지키려고 발급 시각도 함께 과거로 옮긴다.
		jdbc.update("UPDATE oauth_signup_ticket SET created_at = now() - interval '20 minutes', "
				+ "expires_at = now() - interval '1 minute' WHERE provider_subject = ?", subject);

		assertThatThrownBy(() -> accountService.completeSignup(signup.signupTicket(), null, null,
				Map.of("TERMS_OF_SERVICE", true, "PRIVACY_POLICY", true), false, "device-1"))
				.isInstanceOf(AuthException.class)
				.hasFieldOrPropertyWithValue("code", "OAUTH_TICKET_INVALID");
		assertThatThrownBy(() -> accountService.completeSignup("no-such-ticket-" + UUID.randomUUID(), null, null,
				Map.of("TERMS_OF_SERVICE", true, "PRIVACY_POLICY", true), false, "device-1"))
				.isInstanceOf(AuthException.class)
				.hasFieldOrPropertyWithValue("code", "OAUTH_TICKET_INVALID");
		assertThat(identityRepository.findByProviderAndProviderSubject(AuthProvider.GOOGLE, subject)).isEmpty();
	}

	@Test
	@DisplayName("메일 인증을 끝낸 비밀번호 계정과 같은 이메일이면 비밀번호를 묻지 않고 그 계정에 붙는다")
	void verifiedLocalAccountIsAttachedWithoutPassword() {
		AppUser existing = seedLocalAccount("올바른비밀번호1!", true);
		long usersBefore = userRepository.count();

		OAuthAccountService.Outcome outcome = accountService.authenticate(AuthProvider.GOOGLE, profile(), "device-1",
				null, false, false);

		OAuthAccountService.LoggedIn result = (OAuthAccountService.LoggedIn) outcome;
		assertThat(result.user().getUserId()).isEqualTo(existing.getUserId());
		assertThat(result.tokens().accessToken()).isNotBlank();
		assertThat(userRepository.count()).isEqualTo(usersBefore);
		assertThat(identityRepository.findByProviderAndProviderSubject(AuthProvider.GOOGLE, subject))
				.get()
				.satisfies(identity -> assertThat(identity.getUser().getUserId()).isEqualTo(existing.getUserId()));
		// 비밀번호를 묻지 않았으므로 연결 티켓도 안 나간다.
		assertThat(ticketRows("LINK")).isZero();
	}

	@Test
	@DisplayName("같은 이메일로 다른 소셜에 로그인하면 계정이 하나 더 생기지 않고 먼저 만든 계정에 붙는다")
	void secondProviderAttachesToTheFirstSocialAccount() {
		String naverSubject = "naver-" + UUID.randomUUID();
		OAuthAccountService.SignupRequired signup = (OAuthAccountService.SignupRequired) accountService.authenticate(
				AuthProvider.NAVER,
				new OAuthProviderClient.OAuthUserProfile(naverSubject, email, "여행자", "KO", null, null), "device-1",
				null, false, false);
		OAuthAccountService.LoggedIn first = (OAuthAccountService.LoggedIn) accountService.completeSignup(
				signup.signupTicket(), null, null, Map.of("TERMS_OF_SERVICE", true, "PRIVACY_POLICY", true), false,
				"device-1");
		long usersAfterFirst = userRepository.count();

		OAuthAccountService.Outcome outcome = accountService.authenticate(AuthProvider.GOOGLE, profile(), "device-1",
				null, false, false);

		assertThat(((OAuthAccountService.LoggedIn) outcome).user().getUserId()).isEqualTo(first.user().getUserId());
		assertThat(userRepository.count()).isEqualTo(usersAfterFirst);
		assertThat(identityRepository.findAllByUserUserId(first.user().getUserId())).hasSize(2);
	}

	@Test
	@DisplayName("🔴 메일 인증 전인 로컬 계정과 같은 이메일이면 409 + 연결 티켓이고, 비밀번호를 확인하면 그 계정에 붙는다")
	void linkTicketThenPasswordAttachesIdentityToExistingAccount() {
		AppUser existing = seedLocalAccount("올바른비밀번호1!", false);
		long usersBefore = userRepository.count();

		OAuthAccountService.Outcome outcome = accountService.authenticate(AuthProvider.GOOGLE, profile(), "device-1",
				null, false, false);

		assertThat(outcome).isInstanceOf(OAuthAccountService.LinkRequired.class);
		OAuthAccountService.LinkRequired required = (OAuthAccountService.LinkRequired) outcome;
		assertThat(required.linkTicket()).hasSize(43);
		// seedLocalAccount 가 소셜 쪽 이메일을 그 계정의 이메일로 맞춰 두었다 — 가려진 값은 그 이메일에서 나온다.
		assertThat(required.maskedEmail()).isEqualTo(OAuthLoginResponse.mask(email));
		assertThat(required.maskedEmail()).contains("***@").doesNotContain(email.substring(1, 10));
		// 🔴 티켓이 실제로 표에 남아야 한다. 예전에 이 갈래를 409 예외로 던졌더니 트랜잭션이 되돌려져 이 행이 사라졌고,
		//    클라이언트는 DB 에 없는 티켓을 받았다. 이 단정이 그것을 잡은 자리다.
		assertThat(ticketRows("LINK")).isEqualTo(1);
		assertThat(identityRepository.findByProviderAndProviderSubject(AuthProvider.GOOGLE, subject)).isEmpty();

		// 틀린 비밀번호는 붙이지 않는다.
		assertThatThrownBy(() -> accountService.linkWithPassword(required.linkTicket(), "틀린비밀번호1!", "device-1"))
				.isInstanceOf(AuthException.class)
				.hasFieldOrPropertyWithValue("code", "INVALID_CREDENTIALS");
		assertThat(identityRepository.findByProviderAndProviderSubject(AuthProvider.GOOGLE, subject)).isEmpty();

		// 🔴 틀린 비밀번호로 트랜잭션이 되돌려졌으니 티켓 소비도 취소됐다 — 같은 티켓으로 다시 시도할 수 있다.
		OAuthAccountService.LoggedIn result = accountService.linkWithPassword(required.linkTicket(), "올바른비밀번호1!",
				"device-1");

		assertThat(result.user().getUserId()).isEqualTo(existing.getUserId());
		assertThat(identityRepository.findByProviderAndProviderSubject(AuthProvider.GOOGLE, subject)).isPresent();
		assertThat(userRepository.count()).isEqualTo(usersBefore);

		// 붙은 뒤에는 같은 소셜 계정으로 바로 로그인된다.
		OAuthAccountService.Outcome again = accountService.authenticate(AuthProvider.GOOGLE, profile(), "device-1", null,
				false, false);
		assertThat(again).isInstanceOf(OAuthAccountService.LoggedIn.class);
		assertThat(((OAuthAccountService.LoggedIn) again).user().getUserId()).isEqualTo(existing.getUserId());
	}

	@Test
	@DisplayName("로그인한 계정에 소셜을 붙이면 신원이 생기고, 두 번 붙이면 alreadyLinked 로 답한다")
	void linkFromAuthenticatedSession() {
		AppUser me = seedLocalAccount("올바른비밀번호1!");

		OAuthAccountService.LinkedIdentity first = accountService.linkAuthenticated(me.getUserId(), AuthProvider.GOOGLE,
				profile());
		assertThat(first.alreadyLinked()).isFalse();
		assertThat(first.identity().getUser().getUserId()).isEqualTo(me.getUserId());

		OAuthAccountService.LinkedIdentity second = accountService.linkAuthenticated(me.getUserId(), AuthProvider.GOOGLE,
				profile());
		assertThat(second.alreadyLinked()).isTrue();

		// 남의 계정에 붙은 신원은 가져올 수 없다.
		AppUser other = seedLocalAccount("다른비밀번호1!");
		assertThatThrownBy(() -> accountService.linkAuthenticated(other.getUserId(), AuthProvider.GOOGLE, profile()))
				.isInstanceOf(AuthException.class)
				.hasFieldOrPropertyWithValue("code", "OAUTH_IDENTITY_TAKEN");
	}

	@Test
	@DisplayName("이메일을 주지 않는 provider 도 가입할 수 있다 — 계정은 생기고 신원의 이메일만 비어 있다")
	void providerWithoutEmailCanStillSignUp() {
		String kakaoSubject = "kakao-" + UUID.randomUUID();
		OAuthAccountService.Outcome outcome = accountService.authenticate(AuthProvider.KAKAO,
				new OAuthProviderClient.OAuthUserProfile(kakaoSubject, null, "카카오 사용자", "KO", null, null), "device-1",
				null, false, false);

		OAuthAccountService.SignupRequired signup = (OAuthAccountService.SignupRequired) outcome;
		assertThat(signup.emailProvided()).isFalse();

		OAuthAccountService.LoggedIn result = (OAuthAccountService.LoggedIn) accountService.completeSignup(
				signup.signupTicket(), null, null,
				Map.of("TERMS_OF_SERVICE", true, "PRIVACY_POLICY", true), false, "device-1");

		assertThat(result.tokens().accessToken()).isNotBlank();
		assertThat(result.email()).isNull();
		assertThat(identityRepository.findByProviderAndProviderSubject(AuthProvider.KAKAO, kakaoSubject))
				.get()
				.satisfies(identity -> assertThat(identity.getProviderEmail()).isNull());
	}

	private AppUser seedLocalAccount(String rawPassword) {
		return seedLocalAccount(rawPassword, true);
	}

	/**
	 * 이메일·비밀번호로 가입한 계정 하나.
	 *
	 * <p>메일 인증 여부가 S15P21E201-923 부터 갈래를 가른다 — 인증을 끝낸 주소는 우리가 확인한 주소라 소셜이 바로
	 * 붙고, 인증 전이면 예전처럼 비밀번호를 묻는다.
	 */
	private AppUser seedLocalAccount(String rawPassword, boolean emailVerified) {
		String localEmail = "local-" + UUID.randomUUID() + "@example.com";
		AppUser user = userRepository.save(AppUser.register("기존 사용자", "KO", Instant.now(), "2026-01",
				PersonalizationMode.EXPLICIT_ONLY, UserStatus.ACTIVE));
		LocalCredential credential = credentialRepository.save(
				LocalCredential.create(user, localEmail, passwordEncoder.encode(rawPassword)));
		if (emailVerified) {
			jdbc.update("UPDATE local_credential SET email_verified_at = now() WHERE local_credential_id = ?",
					credential.getLocalCredentialId());
		}
		// 소셜 쪽 이메일이 이 계정과 같아야 연결 흐름에 걸린다.
		this.email = localEmail;
		return user;
	}
}
