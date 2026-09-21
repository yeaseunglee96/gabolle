package com.gabolle.backend.auth.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.auth.config.AuthProperties;
import com.gabolle.backend.auth.domain.LocalCredential;
import com.gabolle.backend.auth.repository.LocalCredentialRepository;

/**
 * 로그인 실패를 세어 남긴다.
 *
 * <p>세는 일은 반드시 {@link Propagation#REQUIRES_NEW}(바깥과 별개의 새 트랜잭션에서 커밋한다)여야
 * 한다. {@code LocalAuthService.login} 은 비밀번호가 틀리면 예외를 던져 트랜잭션을 통째로
 * 되돌리므로, 그 안에서 올리면 올린 것까지 사라져 영영 임계치에 닿지 않는다 — 그러면서 응답은
 * 정상이라 아무도 눈치채지 못한다.
 *
 * <p>성공 경로는 여기 없다. 바깥 트랜잭션이 그대로 커밋되므로 엔티티를 고치는 것으로 충분하다.
 */
@Service
@Profile({"db", "dev"})
public class LoginAttemptGuard {

	private final LocalCredentialRepository credentialRepository;

	private final AuthProperties properties;

	public LoginAttemptGuard(LocalCredentialRepository credentialRepository, AuthProperties properties) {
		this.credentialRepository = credentialRepository;
		this.properties = properties;
	}

	/**
	 * 실패를 한 번 세고, 정해진 횟수에 닿았으면 잠근다. 이번 실패까지 포함한 연속 실패 횟수(1부터
	 * 시작)를 돌려준다.
	 *
	 * <p>바깥 트랜잭션의 엔티티를 쓰지 않고 아이디로 다시 읽는다 — 새 트랜잭션은 자기만의 영속성
	 * 컨텍스트를 갖는다. 계정이 그 사이 사라졌으면 아무 일도 하지 않고 0 을 돌려준다.
	 *
	 * <p>반환값은 {@code recordFailedLogin} 호출 전에 미리 계산해야 한다. 그 메서드는 임계치에
	 * 닿으면 잠그면서 내부 카운터를 0 으로 되돌리므로, 호출 뒤에 읽으면 다섯 번째 실패인데도
	 * 0 이 나온다.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public int recordFailure(UUID credentialId, Instant now) {
		return this.credentialRepository.findById(credentialId).map(credential -> {
			int attempts = credential.getFailedLoginAttempts() + 1;
			credential.recordFailedLogin(now, this.properties.getLoginFailureThreshold(),
					this.properties.getLoginLockoutDuration());
			return attempts;
		}).orElse(0);
	}

	/**
	 * 지금 잠겨 있는지 다시 읽어 확인한다.
	 *
	 * <p>{@link #recordFailure} 가 새 트랜잭션에서 커밋했기 때문에, 바깥 트랜잭션이 들고 있는
	 * 엔티티는 그 결과를 모른다. 방금 잠긴 것인지 알려면 다시 읽어야 한다.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
	public boolean isLocked(UUID credentialId, Instant now) {
		return this.credentialRepository.findById(credentialId)
				.map(credential -> credential.isLoginLocked(now))
				.orElse(false);
	}

	/** 언제 풀리는지. 응답 메시지에 남은 시간을 적으려고 쓴다. */
	@Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
	public Instant lockedUntil(UUID credentialId) {
		return this.credentialRepository.findById(credentialId)
				.map(LocalCredential::getLoginLockedUntil)
				.orElse(null);
	}
}
