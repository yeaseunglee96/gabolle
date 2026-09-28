package com.gabolle.backend.auth.service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.gabolle.backend.auth.config.AuthProperties;
import com.gabolle.backend.auth.domain.LocalCredential;
import com.gabolle.backend.auth.repository.LocalCredentialRepository;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 로그인 실패를 세어 남긴다.
 *
 * <p>세는 일은 반드시 {@link Propagation#REQUIRES_NEW}(바깥과 별개의 새 트랜잭션에서 커밋한다)여야
 * 한다. {@code LocalAuthService.login} 은 비밀번호가 틀리면 예외를 던져 트랜잭션을 통째로
 * 되돌리므로, 그 안에서 올리면 올린 것까지 사라져 영영 임계치에 닿지 않는다 — 그러면서 응답은
 * 정상이라 아무도 눈치채지 못한다.
 *
 * <p>성공 경로는 여기 없다. 바깥 트랜잭션이 그대로 커밋되므로 엔티티를 고치는 것으로 충분하다.
 *
 * <p>🔴 S15P21E201-1549 — 계정 단위 카운터만으로는 «한 IP 가 계정을 계속 바꿔가며 시도하는»
 * 크리덴셜 스터핑을 못 막는다(계정마다 카운터가 따로라 계정을 바꾸면 무제한이다). 그래서 IP 단위
 * 카운터를 병행한다 — 계정 단위를 대체하지 않는다.
 *
 * <p>IP 단위는 메모리(이 인스턴스 안)에만 둔다. 계정 단위처럼 DB 에 두지 않는 이유는, 이 값이
 * 지키려는 것이 "이 계정이 안전한가"가 아니라 "지금 이 순간 이 서버가 감당할 스팸인가"이기
 * 때문이다 — 재시작하면 잊혀도 되는 값이다. 대신 서버가 여러 대(다중 인스턴스)면 인스턴스마다
 * 따로 세므로, 공격자가 라운드로빈에 걸리면 실제 한도는 인스턴스 수만큼 늘어난다. 이 한계는
 * 알려진 채로 남긴다 — 다중 인스턴스 배포로 가면 Redis 등 공유 저장소로 옮겨야 한다.
 */
@Service
@Profile({"db", "dev"})
public class LoginAttemptGuard {

	/**
	 * 계정 단위(기본 5회)보다 훨씬 넉넉하게 잡는다 — 사무실·카페처럼 여러 사람이 같은 IP를 쓰는
	 * 곳에서 우연히 겹치는 정상 실패까지 막으면 안 된다. 30회는 "한 IP에서 짧은 시간에 계정을
	 * 바꿔가며 대량으로 시도하는" 자동화만 걸러내는 값이다.
	 */
	private static final int IP_FAILURE_THRESHOLD = 30;

	/** 이 시간 안의 실패만 센다 — 지나면 창이 새로 열린다. */
	private static final Duration IP_FAILURE_WINDOW = Duration.ofMinutes(15);

	/** 걸리면 이만큼 잠근다. 계정 단위(5분)보다 길게 잡는다 — IP 단위는 대량 자동화를 막는 것이 목적이라 짧으면 무의미하다. */
	private static final Duration IP_LOCKOUT_DURATION = Duration.ofMinutes(15);

	/** IP 하나의 현재 창 상태. 불변 — 갱신은 항상 새 값으로 교체한다({@link ConcurrentHashMap#compute}). */
	private record IpWindow(int count, Instant windowStart, Instant lockedUntil) {
	}

	private final ConcurrentHashMap<String, IpWindow> ipAttempts = new ConcurrentHashMap<>();

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
		recordIpFailure(now);
		return this.credentialRepository.findById(credentialId).map(credential -> {
			int attempts = credential.getFailedLoginAttempts() + 1;
			credential.recordFailedLogin(now, this.properties.getLoginFailureThreshold(),
					this.properties.getLoginLockoutDuration());
			return attempts;
		}).orElse(0);
	}

	/**
	 * 이번 요청의 클라이언트 IP에 실패를 하나 더한다. 계정이 있든 없든(존재하지 않는 이메일로
	 * 시도해도) 센다 — IP 단위는 계정 존재 여부와 무관한 요청량 자체를 본다.
	 *
	 * <p>DB가 아니라 이 인스턴스의 메모리에만 남는다 — 클래스 Javadoc의 한계를 참고.
	 */
	private void recordIpFailure(Instant now) {
		String ip = resolveClientIp();
		this.ipAttempts.compute(ip, (key, existing) -> {
			if (existing == null || Duration.between(existing.windowStart(), now).compareTo(IP_FAILURE_WINDOW) > 0) {
				return new IpWindow(1, now, null);
			}
			int count = existing.count() + 1;
			Instant lockedUntil = count >= IP_FAILURE_THRESHOLD ? now.plus(IP_LOCKOUT_DURATION)
					: existing.lockedUntil();
			return new IpWindow(count, existing.windowStart(), lockedUntil);
		});
	}

	/**
	 * 가입되지 않은 이메일로 로그인을 시도했을 때도 IP 실패로 센다.
	 *
	 * <p>{@code LocalAuthService.login}은 이메일이 없으면 계정을 못 찾아 {@link #recordFailure}
	 * (계정 아이디가 있어야 한다)를 부를 수 없다 — 그런데 IP 단위가 막으려는 크리덴셜 스터핑은
	 * 오히려 "존재하지 않는 이메일을 계속 바꿔가며 시도하는" 경우가 더 흔하다. 그래서 계정을 못
	 * 찾은 경로 전용으로 이 메서드를 따로 둔다.
	 */
	public void recordIpFailureForUnknownAccount(Instant now) {
		recordIpFailure(now);
	}

	/**
	 * 지금 이 요청의 클라이언트 IP가 잠겨 있는가. {@link #recordFailure}로 이미 채워진 메모리
	 * 상태만 읽으므로 트랜잭션이 필요 없다.
	 */
	public boolean isIpLocked(Instant now) {
		IpWindow window = this.ipAttempts.get(resolveClientIp());
		return window != null && window.lockedUntil() != null && now.isBefore(window.lockedUntil());
	}

	/** 언제 풀리는지 — 계정 단위 {@link #lockedUntil}과 같은 용도. */
	public Instant ipLockedUntil() {
		IpWindow window = this.ipAttempts.get(resolveClientIp());
		return window == null ? null : window.lockedUntil();
	}

	/**
	 * 현재 요청의 클라이언트 IP. {@code SecurityEventLogger.resolveRemoteIp}와 같은 방식이다 —
	 * 그 클래스가 이번 작업 범위 밖이라 공용 유틸로 뽑아 공유하지 못하고 그대로 옮겨 적었다.
	 */
	private String resolveClientIp() {
		RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
		if (!(attributes instanceof ServletRequestAttributes servletRequestAttributes)) {
			return "unknown";
		}
		HttpServletRequest request = servletRequestAttributes.getRequest();
		String forwardedFor = request.getHeader("X-Forwarded-For");
		if (forwardedFor != null && !forwardedFor.isBlank()) {
			return forwardedFor.split(",")[0].trim();
		}
		String remoteAddr = request.getRemoteAddr();
		return remoteAddr == null || remoteAddr.isBlank() ? "unknown" : remoteAddr;
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
