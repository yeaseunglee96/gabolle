package com.gabolle.backend.common.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 보안 이벤트를 구조화된 로그로 남긴다. 사람이 읽는 문장이 아니라 {@code key=value} 형태로 고정한다 —
 * 필드 순서와 이름을 바꾸면 그 필드로 걸러 보던 대시보드가 조용히 끊긴다.
 *
 * <p>이메일 원문은 남기지 않는다. 같은 사람의 반복 실패를 셀 수 있어야 하므로 SHA-256 해시의 앞
 * {@value #EMAIL_HASH_LENGTH}자만 남긴다. 완전한 방어는 아니다 — 원문 이메일을 대상으로 한 레인보우
 * 테이블은 그대로 성립한다. 비밀번호·토큰·티켓 값은 해시조차 남기지 않는다.
 *
 * <p>로그 수준: 실패 한 건은 정상 운영 중에도 늘 있는 일이라 {@code INFO} 고, 계정 잠금은 대응이
 * 필요할 수 있어 {@code WARN} 이다. 실패 한 건까지 {@code WARN} 이면 경고가 잡음이 된다.
 *
 * <p>급증 알림은 로그인 실패·계정 잠금·인가 실패에만 건다. {@link SecurityEvent#AUTH_TOKEN_REJECTED} 는
 * 만료된 토큰으로 뒤늦게 요청하는 정상 동작도 포함해 급증 자체가 공격 신호로 약하다. 필요해지면
 * {@link SecurityAlertNotifier#recordAndMaybeAlert} 호출 한 줄을 더하면 된다.
 */
@Component
public class SecurityEventLogger {

	private static final Logger log = LoggerFactory.getLogger(SecurityEventLogger.class);

	/** SHA-256 해시(64자) 중 로그에 남기는 앞자리 수. */
	private static final int EMAIL_HASH_LENGTH = 12;

	private final SecurityAlertNotifier alertNotifier;

	public SecurityEventLogger(SecurityAlertNotifier alertNotifier) {
		this.alertNotifier = alertNotifier;
	}

	/** 로그인 실패 지점(비밀번호 불일치)에서 남긴다. {@code attempts} 는 이번 실패까지 포함한 연속 실패 횟수다. */
	public void loginFailure(String email, int attempts) {
		log.info("event={} emailHash={} remoteIp={} attempts={} outcome=REJECTED", SecurityEvent.AUTH_LOGIN_FAILURE,
				hashEmail(email), resolveRemoteIp(), attempts);
		this.alertNotifier.recordAndMaybeAlert(SecurityEvent.AUTH_LOGIN_FAILURE);
	}

	/**
	 * 가입되지 않은 이메일로 로그인이 실패한 지점에서 남긴다. 유출된 계정 목록을 넓게 뿌리는 공격은
	 * 시도 대부분이 이 경로로 들어오므로, 여기에 로그가 없으면 급증 경보도 안 울린다.
	 *
	 * <p>{@code attempts} 를 안 싣는다. 실패 횟수는 {@code local_credential} 행에 세는데 계정이 없으면
	 * 셀 행이 없고, 없는 숫자를 지어내면 로그를 세는 쪽이 "이 사람의 첫 실패" 로 잘못 읽는다. 대신
	 * {@code accountExists=false} 로 왜 없는지를 밝힌다.
	 */
	public void loginFailureForUnknownAccount(String attemptedEmail) {
		log.info("event={} emailHash={} remoteIp={} accountExists=false outcome=REJECTED",
				SecurityEvent.AUTH_LOGIN_FAILURE, hashEmail(attemptedEmail), resolveRemoteIp());
		this.alertNotifier.recordAndMaybeAlert(SecurityEvent.AUTH_LOGIN_FAILURE);
	}

	/** 방금 그 실패로 계정이 잠긴(임계치에 닿은) 판정 지점에서 남긴다. */
	public void accountLocked(String email, int attempts) {
		log.warn("event={} emailHash={} remoteIp={} attempts={} outcome=LOCKED", SecurityEvent.AUTH_ACCOUNT_LOCKED,
				hashEmail(email), resolveRemoteIp(), attempts);
		this.alertNotifier.recordAndMaybeAlert(SecurityEvent.AUTH_ACCOUNT_LOCKED);
	}

	/**
	 * 이미 잠긴 계정으로 로그인이 다시 시도된 지점에서 남긴다. {@link #accountLocked} 는 임계치에 닿는
	 * 순간에만 남으므로 잠긴 뒤의 시도는 이 메서드로만 보인다.
	 *
	 * <p>{@code attempts} 를 안 싣는다. 잠긴 뒤에는 실패 횟수를 더 올리지 않아 값이 임계치에 멈춰
	 * 있으므로, 실으면 "아직 임계치에 막 닿았다" 로 잘못 읽힌다. 대신 {@code lockedForSeconds} 로
	 * 같은 잠금 구간 안의 반복인지 새 잠금인지 구분한다.
	 */
	public void lockedAccountAttempt(String email, long lockedForSeconds) {
		log.warn("event={} emailHash={} remoteIp={} lockedForSeconds={} outcome=REJECTED",
				SecurityEvent.AUTH_LOCKED_ACCOUNT_ATTEMPT, hashEmail(email), resolveRemoteIp(), lockedForSeconds);
		this.alertNotifier.recordAndMaybeAlert(SecurityEvent.AUTH_LOCKED_ACCOUNT_ATTEMPT);
	}

	/**
	 * 허용 목록에 없는 redirect URI 로 소셜 로그인 챌린지를 요청한 지점에서 남긴다.
	 *
	 * <p>URI 를 그대로 싣지 않고 호스트만 싣는다. 이 값은 공격자가 정한 문자열이라 경로와 질의에
	 * 훔친 표나 남의 개인정보가 섞여 있을 수 있고, 그대로 적으면 우리가 저장하지 않기로 한 것을
	 * 공격자가 우리 로그에 대신 적어 넣게 된다.
	 */
	public void oauthRedirectRejected(String provider, String rejectedRedirectUri) {
		log.warn("event={} provider={} remoteIp={} redirectHost={} outcome=REJECTED",
				SecurityEvent.AUTH_OAUTH_REDIRECT_REJECTED, provider, resolveRemoteIp(),
				hostOf(rejectedRedirectUri));
		this.alertNotifier.recordAndMaybeAlert(SecurityEvent.AUTH_OAUTH_REDIRECT_REJECTED);
	}

	/**
	 * 주소의 호스트만 뽑는다. 실패하면 원문을 남기지 않고 {@code unparsable} 이다. 포트가 있으면 함께
	 * 남긴다 — 같은 호스트의 다른 포트로 돌리려는 시도를 구분해야 한다.
	 */
	private static String hostOf(String uri) {
		if (uri == null || uri.isBlank()) {
			return "none";
		}
		try {
			java.net.URI parsed = java.net.URI.create(uri.trim());
			String host = parsed.getHost();
			if (host == null || host.isBlank()) {
				return "unparsable";
			}
			return parsed.getPort() < 0 ? host : host + ":" + parsed.getPort();
		}
		catch (IllegalArgumentException ex) {
			return "unparsable";
		}
	}

	/** 토큰이 없거나 유효하지 않아 401 이 나가는 지점에서 남긴다. {@code reasonCode} 는 {@code AuthException} 의 코드다. */
	public void tokenRejected(String reasonCode) {
		log.info("event={} remoteIp={} reason={} outcome=REJECTED", SecurityEvent.AUTH_TOKEN_REJECTED,
				resolveRemoteIp(), reasonCode);
	}

	/**
	 * 이미 쓴 갱신 표가 유예 시간 안에 다시 와서 정상 경쟁으로 처리했다. 급증 경보를 일부러 걸지
	 * 않는다 — 거부가 아니라 허용이고, 무차별 대입 경보와 같은 채널로 보내면 진짜 경보가 묻힌다.
	 *
	 * <p>{@code sinceUsedMs} 는 원래 사용 시각과의 간격이다. 이 값이 유예 시간 상한에 자주 붙으면
	 * 유예를 늘려야 하는지 판단할 근거가 된다.
	 */
	public void refreshRace(long sinceUsedMs) {
		log.info("event={} remoteIp={} sinceUsedMs={} outcome=ALLOWED", SecurityEvent.AUTH_REFRESH_RACE,
				resolveRemoteIp(), sinceUsedMs);
	}

	/** 인증은 됐는데 권한이 없어 403 이 나가는 지점에서 남긴다. */
	public void authzDenied(String reasonCode) {
		log.info("event={} remoteIp={} reason={} outcome=REJECTED", SecurityEvent.AUTHZ_DENIED, resolveRemoteIp(),
				reasonCode);
		this.alertNotifier.recordAndMaybeAlert(SecurityEvent.AUTHZ_DENIED);
	}

	private String hashEmail(String email) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(email.getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder(digest.length * 2);
			for (byte value : digest) {
				hex.append(String.format("%02x", value));
			}
			return hex.substring(0, EMAIL_HASH_LENGTH);
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is required by the runtime", exception);
		}
	}

	/**
	 * 현재 요청의 클라이언트 IP. 부르는 쪽이 서비스 계층이라 {@code HttpServletRequest} 를 받지 않으므로
	 * {@link RequestContextHolder} 에서 꺼낸다 — 요청을 처리하는 바로 그 스레드에서 불러야 값이 있다.
	 * 요청 스레드가 아니면 예외를 던지지 않고 {@code "unknown"} 이다.
	 */
	private String resolveRemoteIp() {
		RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
		if (!(attributes instanceof ServletRequestAttributes servletRequestAttributes)) {
			return "unknown";
		}
		HttpServletRequest request = servletRequestAttributes.getRequest();
		String forwardedFor = request.getHeader("X-Forwarded-For");
		if (forwardedFor != null && !forwardedFor.isBlank()) {
			// 배포는 리버스 프록시 뒤에 있어 request.getRemoteAddr() 는 프록시 자신의 주소가 된다.
			return forwardedFor.split(",")[0].trim();
		}
		String remoteAddr = request.getRemoteAddr();
		return remoteAddr == null || remoteAddr.isBlank() ? "unknown" : remoteAddr;
	}
}
