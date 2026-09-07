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
 * 로그인 실패를 세어 남긴다 (S15P21E201-421).
 *
 * <h2>🔴 왜 별도 클래스이고, 왜 새 트랜잭션인가</h2>
 *
 * {@code LocalAuthService.login} 은 {@code @Transactional} 이다. 비밀번호가 틀리면 그 메서드는
 * 예외를 던지고, 그러면 <b>그 트랜잭션이 통째로 되돌려진다.</b> 실패 횟수를 그 안에서 올리면 올린
 * 것까지 같이 사라져서 <b>영영 5회에 닿지 않는다.</b> 기능이 있는데 한 번도 동작하지 않고, 응답은
 * 정상이라 아무도 눈치채지 못한다.
 *
 * <p>그래서 세는 일만 {@link Propagation#REQUIRES_NEW}(**바깥 트랜잭션과 별개로 새 트랜잭션을 열고
 * 거기서 커밋한다**)로 떼어 낸다. 바깥이 되돌려져도 여기서 커밋한 것은 남는다.
 *
 * <p>{@code noRollbackFor} 로 바깥 트랜잭션을 커밋시키는 방법도 있었다. 한 줄이라 짧지만, 나중에
 * 그 메서드에 다른 쓰기가 하나라도 들어오면 <b>실패했는데도 조용히 커밋된다.</b> 되돌아가야 할
 * 것과 남아야 할 것을 클래스로 갈라 두는 편이 다음 사람에게 안전하다.
 *
 * <h2>성공했을 때는 왜 여기 없나</h2>
 *
 * 성공 경로는 바깥 트랜잭션이 그대로 커밋되므로 엔티티를 고치는 것만으로 충분하다. 연결을 하나 더
 * 여는 값을 낼 이유가 없다.
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
	 * 시작)를 돌려준다 — S15P21E201-682 가 이 값을 로그의 {@code attempts} 에 싣는다.
	 *
	 * <p>바깥 트랜잭션의 엔티티를 쓰지 않고 아이디로 다시 읽는다. 새 트랜잭션은 자기만의 영속성
	 * 컨텍스트(**한 트랜잭션이 읽어 둔 엔티티를 담는 곳**)를 갖기 때문이다.
	 *
	 * <p>계정이 그 사이 사라졌으면 아무 일도 하지 않고 0을 돌려준다. 없는 계정을 세느라 로그인
	 * 응답을 실패로 바꾸지 않는다.
	 *
	 * <p>🔴 반환값을 {@code credential.recordFailedLogin} 호출 <b>전에</b> 미리 계산한다. 그 메서드는
	 * 임계치에 닿으면 잠그면서 내부 카운터를 곧바로 0으로 되돌리므로(잠금 풀린 직후 한 번만 더
	 * 틀려도 재잠기는 것을 막기 위해서다 — {@link LocalCredential#recordFailedLogin} 의 주석 참고),
	 * 호출 뒤에 다시 읽으면 5번째 실패인데도 0이 나온다. "다섯 번째 줄의 attempts가 5" 라는 완료
	 * 기준을 지키려면 되돌려지기 전 값을 붙잡아야 한다.
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
