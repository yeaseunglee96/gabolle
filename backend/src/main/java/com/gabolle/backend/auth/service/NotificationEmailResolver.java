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
 * 이 사용자에게 실제로 닿는 메일 주소.
 *
 * <p>"사용자의 이메일" 은 한 곳에 있지 않다 — 비밀번호 계정은 {@code local_credential} 에, 소셜
 * 전용 계정은 {@code auth_identity.provider_email} 에 있고, 둘 다 없는 사람도 있다(애플과 기본
 * 동의 카카오는 이메일을 주지 않는다). 그래서 판정을 이 한 자리로 모은다.
 *
 * <p>{@code email_valid} 가 명시적으로 거짓인 주소는 쓰지 않는다. 그 주소는 다른 계정으로
 * 옮겨갔을 수 있어, 보내면 남의 우편함에 도착한다. 다만 모름({@code null})은 배제하지 않는다 —
 * 가입 티켓으로 만들어진 신원은 그 값이 {@code null} 로 시작하므로 배제하면 알림이 영영 안 간다.
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
	 * 알림을 보낼 주소. 없으면 비어 있고, 호출자는 그 경우를 정상으로 다뤄야 한다.
	 *
	 * <p>비밀번호 계정의 주소를 먼저 본다 — 그쪽은 메일 인증을 지나야 로그인이 되므로 사용자가
	 * 실제로 받아 본 주소임이 증명돼 있다. 소셜이 준 주소는 그 증명이 약하다.
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
