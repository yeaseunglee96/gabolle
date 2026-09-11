package com.gabolle.backend.auth.service;

import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.auth.domain.AuthIdentity;
import com.gabolle.backend.auth.repository.AuthIdentityRepository;
import com.gabolle.backend.auth.repository.LocalCredentialRepository;

/**
 * 이 사용자에게 <b>실제로 닿는</b> 메일 주소 — S15P21E201-794.
 *
 * <h2>왜 이것이 따로 필요한가</h2>
 * "사용자의 이메일" 이 한 곳에 있지 않다. 비밀번호로 가입한 사람은 {@code local_credential} 에,
 * 소셜로만 가입한 사람은 {@code auth_identity.provider_email} 에 있고, <b>둘 다 없는 사람도 있다</b> —
 * 애플과 기본 동의 카카오는 이메일을 주지 않는다.
 *
 * <p>알림을 보내야 하는 자리마다 이 세 경우를 각자 판단하면 언젠가 한 곳이 틀린다. 특히 마지막
 * 경우(주소가 없다)를 잊으면 그 자리에서 {@code null} 로 메일을 보내려 하다 예외가 나고, 알림이
 * 곁가지인 작업에서는 그 예외가 본 작업까지 되돌린다.
 *
 * <h2>🔴 유효하지 않다고 표시된 주소는 쓰지 않는다</h2>
 * 카카오는 그 주소가 <b>다른 카카오계정으로 옮겨가면</b> 유효하지 않다고 답하고, 우리는 그 신호를
 * {@code auth_identity.email_valid} 에 저장한다(S15P21E201-741). 그 주소로 알림을 보내면
 * <b>남의 우편함에 "당신의 기록이 지워졌습니다" 가 도착한다.</b> 로그인 판정에서 그 주소를 배제하는
 * 것과 같은 이유가 알림에도 그대로 적용된다.
 *
 * <p>다만 <b>모름({@code null})은 배제하지 않는다.</b> 가입 티켓으로 만들어진 신원은 두 값이
 * {@code null} 로 시작하고 다음 로그인에 채워진다({@code OAuthAccountService.completeSignup} 주석).
 * 모름을 배제하면 그 사람에게는 알림이 영영 안 간다.
 */
@Service
@Profile({ "db", "dev" })
public class NotificationEmailResolver {

	private final LocalCredentialRepository credentialRepository;

	private final AuthIdentityRepository identityRepository;

	public NotificationEmailResolver(LocalCredentialRepository credentialRepository,
			AuthIdentityRepository identityRepository) {
		this.credentialRepository = credentialRepository;
		this.identityRepository = identityRepository;
	}

	/**
	 * 알림을 보낼 주소. 없으면 비어 있다 — <b>호출자는 그 경우를 정상으로 다뤄야 한다.</b>
	 *
	 * <p>비밀번호 계정의 주소를 먼저 본다. 그쪽은 가입 때 메일 인증을 지나야 로그인이 되므로
	 * (`LocalAuthService.login` 이 {@code emailVerifiedAt == null} 을 거부한다) 사용자가 실제로
	 * 받아 본 주소임이 증명돼 있다. 소셜이 준 주소는 그 증명이 약하다 — 네이버는 검증 여부를
	 * 알려주는 칸조차 없다.
	 */
	@Transactional(readOnly = true)
	public Optional<String> resolve(UUID userId) {
		if (userId == null) {
			return Optional.empty();
		}
		Optional<String> local = this.credentialRepository.findByUserUserId(userId)
				.map(credential -> credential.getEmail())
				.filter(NotificationEmailResolver::usable);
		if (local.isPresent()) {
			return local;
		}
		// 여럿이면 가장 최근에 연결한 것을 쓴다 — 사용자가 지금 쓰는 주소에 가장 가깝다.
		return this.identityRepository.findAllByUserUserId(userId).stream()
				.filter((identity) -> identity.getUnlinkedAt() == null)
				.filter((identity) -> !Boolean.FALSE.equals(identity.getEmailValid()))
				.filter((identity) -> usable(identity.getProviderEmail()))
				.max(Comparator.comparing(AuthIdentity::getLinkedAt))
				.map(AuthIdentity::getProviderEmail);
	}

	private static boolean usable(String email) {
		return email != null && !email.isBlank();
	}
}
