package com.gabolle.backend.auth.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.auth.domain.AuthIdentity;
import com.gabolle.backend.auth.domain.AuthProvider;
import com.gabolle.backend.auth.domain.LocalCredential;
import com.gabolle.backend.auth.repository.AuthIdentityRepository;
import com.gabolle.backend.auth.repository.LocalCredentialRepository;

/**
 * 연결된 소셜 계정을 보여주고 뗀다 — S15P21E201-1317.
 *
 * <h2>왜 필요했나</h2>
 * 붙이는 길만 있고 <b>떼는 길이 없었다.</b> 그래서 잘못 연결했거나 그 소셜 계정을 더 안 쓰게 되어도
 * 되돌릴 방법이 계정을 통째로 지우는 것밖에 없었다. 설정 화면은 어떤 계정이 붙어 있는지조차 못
 * 보여주고 있었다 — 눌러 봐야 「이미 연결되어 있어요」로 알 수 있었다.
 *
 * <h2>🔴 마지막 로그인 수단은 못 뗀다</h2>
 * 소셜로만 가입한 사람에게는 비밀번호가 없다. 그 사람이 마지막 연결을 떼면 <b>다시 로그인할 수
 * 없다.</b> 이 판정은 <b>서버가</b> 한다 — 화면이 판단하면 화면을 안 거치는 요청 하나로 계정이
 * 잠긴다.
 *
 * <p>🔴 <b>비밀번호 줄이 있다는 것만으로는 안 된다.</b> 메일 인증을 안 끝낸 비밀번호로는 로그인이
 * 거부되므로, 그것을 「다른 수단」으로 세면 같은 사고가 난다. 판정은
 * {@link LocalCredential#canSignIn()} 한 곳에 있고 로그인도 같은 것을 본다.
 */
@Service
@Profile({ "db", "dev" })
public class LinkedIdentityService {

	private final AuthIdentityRepository identityRepository;

	private final LocalCredentialRepository credentialRepository;

	private final Clock clock;

	public LinkedIdentityService(AuthIdentityRepository identityRepository,
			LocalCredentialRepository credentialRepository, Clock clock) {
		this.identityRepository = identityRepository;
		this.credentialRepository = credentialRepository;
		this.clock = clock;
	}

	/**
	 * 지금 붙어 있는 소셜 계정들.
	 *
	 * <p>끊긴 연결은 안 담는다 — 화면이 묻는 것은 「지금 무엇으로 들어올 수 있나」다.
	 */
	@Transactional(readOnly = true)
	public LinkedIdentities list(UUID userId) {
		List<AuthIdentity> active = activeIdentities(userId);
		boolean withPassword = canSignInWithPassword(userId);
		boolean removable = canUnlink(withPassword, active.size());
		List<LinkedIdentitySummary> items = active.stream()
				.sorted(Comparator.comparing(AuthIdentity::getLinkedAt)
						.thenComparing(identity -> identity.getProvider().name()))
				.map(identity -> new LinkedIdentitySummary(identity.getProvider().name(), identity.getProviderEmail(),
						identity.getLinkedAt(), removable))
				.toList();
		return new LinkedIdentities(items, withPassword);
	}

	/**
	 * 소셜 연결 하나를 뗀다.
	 *
	 * <p>🔴 <b>안 붙어 있으면 성공이다.</b> 두 번 누르거나 목록이 낡은 상태로 눌렀을 때 실패로
	 * 답하면, 사용자는 <b>실제로 끝난 일을 실패로 본다.</b> 팔로우 해제가 이미 같은 방식이다.
	 *
	 * @throws AuthException {@code LAST_SIGN_IN_METHOD}(409) 이걸 떼면 들어올 길이 없어진다
	 */
	@Transactional
	public void unlink(UUID userId, AuthProvider provider) {
		List<AuthIdentity> active = activeIdentities(userId);
		Optional<AuthIdentity> target = active.stream()
				.filter(identity -> identity.getProvider() == provider)
				.findFirst();
		if (target.isEmpty()) {
			return;
		}
		if (!canUnlink(canSignInWithPassword(userId), active.size())) {
			throw new AuthException("LAST_SIGN_IN_METHOD",
					"이 계정에 남은 마지막 로그인 수단이에요. 떼면 다시 로그인할 수 없어요.", HttpStatus.CONFLICT);
		}
		target.get().unlink(this.clock.instant());
		this.identityRepository.save(target.get());
	}

	/** 이걸 떼도 들어올 길이 남는가 — 비밀번호가 있거나, 소셜 연결이 둘 이상이거나. */
	private static boolean canUnlink(boolean canSignInWithPassword, int activeCount) {
		return canSignInWithPassword || activeCount >= 2;
	}

	private List<AuthIdentity> activeIdentities(UUID userId) {
		return this.identityRepository.findAllByUserUserId(userId).stream().filter(AuthIdentity::isActive).toList();
	}

	private boolean canSignInWithPassword(UUID userId) {
		return this.credentialRepository.findByUserUserId(userId).filter(LocalCredential::canSignIn).isPresent();
	}

	/**
	 * @param provider {@code KAKAO}·{@code NAVER}·{@code GOOGLE}·{@code APPLE}
	 * @param providerEmail 그 소셜이 알려 준 이메일. <b>안 주는 경우가 있어서</b> {@code null} 일
	 *                      수 있다 — 카카오 기본 동의는 이메일을 안 준다
	 * @param canUnlink 이걸 떼도 이 계정에 들어올 길이 남는가. 거짓이면 화면이 「연결 해제」를
	 *                  누를 수 없게 하고 이유를 적는다
	 */
	public record LinkedIdentitySummary(String provider, String providerEmail, Instant linkedAt,
			boolean canUnlink) {
	}

	/**
	 * @param canSignInWithPassword 비밀번호로 <b>지금</b> 로그인할 수 있는가. 화면이 「비밀번호를
	 *                              만들면 뗄 수 있어요」를 안내할 때 쓴다. 메일 인증을 안 끝낸
	 *                              비밀번호는 거짓이다 — 그것으로는 로그인이 거부되기 때문이다
	 */
	public record LinkedIdentities(List<LinkedIdentitySummary> items, boolean canSignInWithPassword) {
	}
}
