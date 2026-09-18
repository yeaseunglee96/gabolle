package com.gabolle.backend.auth.service;

import com.gabolle.backend.auth.domain.AuthIdentity;
import com.gabolle.backend.auth.repository.AuthIdentityRepository;
import com.gabolle.backend.auth.repository.LocalCredentialRepository;
import com.gabolle.backend.user.domain.AppUser;
import com.gabolle.backend.user.domain.UserStatus;
import com.gabolle.backend.user.repository.AppUserRepository;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile({"db", "dev"})
public class CurrentUserService {

	private final AppUserRepository userRepository;
	private final LocalCredentialRepository localCredentialRepository;
	private final AuthIdentityRepository authIdentityRepository;

	public CurrentUserService(AppUserRepository userRepository, LocalCredentialRepository localCredentialRepository,
			AuthIdentityRepository authIdentityRepository) {
		this.userRepository = userRepository;
		this.localCredentialRepository = localCredentialRepository;
		this.authIdentityRepository = authIdentityRepository;
	}

	/**
	 * 지금 로그인한 사람의 계정과 대표 이메일을 읽는다.
	 *
	 * <p><b>이메일은 없을 수 있다.</b> 애플은 그 사용자의 맨 처음 인증 때만 이메일을 주고, 사용자가
	 * "이메일 숨기기" 를 고르거나 우리가 아직 이메일을 요청하지 않던 시절에 가입했다면 로컬 비밀번호
	 * 계정도 {@code auth_identity.provider_email} 도 둘 다 비어 있다. 그런 계정에서 이 메서드가
	 * 500 을 던지고 있었고, 그 결과 애플 로그인이 서버까지 성공한 뒤 앱이 곧바로 부르는
	 * {@code GET /api/v1/auth/me} 가 매번 실패했다 — S15P21E201-893.
	 *
	 * <p>없을 때 {@code null} 을 돌려주는 것은 이 클래스가 새로 정하는 규칙이 아니라 <b>로그인 경로가
	 * 이미 쓰던 규칙</b>이다. {@code OAuthAccountService.login()} 은 {@code getProviderEmail()} 을
	 * 그대로 넘기고 {@code AuthUserResponse.email} 은 평범한 nullable 문자열이다. 여기서만 던지면
	 * 같은 계정을 두 경로가 다르게 판정하게 된다.
	 */
	@Transactional(readOnly = true)
	public CurrentUser get(UUID userId) {
		AppUser user = userRepository.findById(userId)
				.filter(candidate -> candidate.getStatus() == UserStatus.ACTIVE)
				.orElseThrow(() -> new AuthException("ACCOUNT_UNAVAILABLE", "사용할 수 없는 계정입니다.",
						HttpStatus.UNAUTHORIZED));

		String email = localCredentialRepository.findByUserUserId(userId)
				.map(credential -> credential.getEmail())
				.orElseGet(() -> authIdentityRepository.findAllByUserUserId(userId).stream()
						.filter(AuthIdentity::isActive)
						.map(AuthIdentity::getProviderEmail)
						.filter(candidate -> candidate != null && !candidate.isBlank())
						.findFirst()
						.orElse(null));

		return new CurrentUser(user, email);
	}

	public record CurrentUser(AppUser user, String email) {
	}
}
