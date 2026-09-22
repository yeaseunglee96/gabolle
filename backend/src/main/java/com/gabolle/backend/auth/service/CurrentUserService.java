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
	 * <p>이메일은 없을 수 있고, 그때는 {@code null} 이다 — 예외로 만들면 안 된다. 비밀번호 계정도
	 * {@code auth_identity.provider_email} 도 비어 있는 계정이 실제로 있고(애플의 이메일 숨기기
	 * 등), 로그인 경로는 그 값을 그대로 넘기므로 여기서만 던지면 같은 계정을 두 경로가 다르게
	 * 판정하게 된다.
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
