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
						.orElseThrow(() -> new AuthException("ACCOUNT_EMAIL_UNAVAILABLE",
								"계정 이메일을 확인할 수 없습니다.", HttpStatus.INTERNAL_SERVER_ERROR)));

		return new CurrentUser(user, email);
	}

	public record CurrentUser(AppUser user, String email) {
	}
}
