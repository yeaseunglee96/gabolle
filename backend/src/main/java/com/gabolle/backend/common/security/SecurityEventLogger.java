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
 * 보안 이벤트(S15P21E201-682, OWASP A09 — 보안 로깅·모니터링 실패)를 <b>구조화된 로그</b>로 남긴다.
 *
 * <h2>🔴 "구조화된" 이 핵심이다</h2>
 *
 * 사람이 읽는 문장이 아니라 {@code key=value} 형태로 고정한다 — 로그 수집기가 {@code event=} 값으로
 * 걸러 세고, 임계치를 넘는지 기계가 판단할 수 있어야 하기 때문이다. 필드 순서와 이름을 바꾸면 그
 * 필드로 걸러 보던 대시보드가 조용히 끊긴다.
 *
 * <h2>개인정보를 남기지 않는다</h2>
 *
 * 이메일 원문은 절대 남기지 않는다. 같은 사람의 반복 실패를 셀 수 있어야 하므로 대신 SHA-256 해시의
 * 앞 {@value #EMAIL_HASH_LENGTH}자만 남긴다 — 전체 해시를 남기면 "무차별 대입으로 이메일을 되찾을
 * 수 있다"는 주장이 가능해진다(레인보우 테이블 공격 대상이 여전히 원문 이메일이라 짧게 잘라도
 * 안전이 크게 늘지는 않지만, 짧게 자르면 적어도 이 로그 한 줄만으로 원문을 그대로 복원하겠다고
 * 나서는 문턱을 없애지 않는다는 뜻이지 완전한 방어는 아니다). 비밀번호·토큰·티켓 값은 해시조차
 * 남기지 않는다 — 애초에 이 클래스의 어떤 메서드도 그런 값을 매개변수로 받지 않는다.
 *
 * <h2>왜 {@link com.gabolle.backend.auth.service.SessionTokenGenerator#hash} 를 그대로 재사용하지 않았나</h2>
 *
 * 그 메서드는 전체 SHA-256 해시(64자)를 돌려준다. 여기서 필요한 것은 앞부분만이라 얻은 값을 다시
 * 자르면 되지만, 그러려면 이 클래스가 {@code auth.service} 패키지의 컴포넌트를 스프링 빈으로
 * 주입받아야 한다. 이 클래스는 {@link GlobalAuthExceptionHandler}(생성자가 없던 전역 advice)와
 * {@code LocalAuthService}·{@code LoginAttemptGuard} 양쪽에서 쓰이는데, 새 빈 의존성을 추가하면
 * 그 클래스들을 직접 생성하는 기존 테스트({@code AuthExceptionRoutingTest}·{@code LocalAuthServiceTest})의
 * 생성자 호출부를 고쳐야 한다 — 그 정도는 불가피해서 실제로 고쳤다(보고서 참고). 반면 SHA-256
 * 자체는 표준 JDK API 한 줄이라 여기서 직접 계산하면 의존성을 하나도 늘리지 않고 끝난다. 티켓도
 * "재사용할 것이 없으면 SHA-256 앞부분만 남기라" 고 이 대안을 명시적으로 허용한다.
 *
 * <h2>로그 수준</h2>
 *
 * {@link SecurityEvent#AUTH_LOGIN_FAILURE}·{@link SecurityEvent#AUTH_TOKEN_REJECTED}·
 * {@link SecurityEvent#AUTHZ_DENIED} 한 건은 정상 운영 중에도 늘 있는 일이다(비밀번호를 잊거나,
 * 만료된 토큰으로 요청하거나, 권한 없는 화면을 실수로 누르는 것은 매일 일어난다). 그래서 {@code INFO}
 * 다. {@link SecurityEvent#AUTH_ACCOUNT_LOCKED} 는 계정이 실제로 잠긴, 더 드물고 대응이 필요할
 * 수 있는 사건이라 {@code WARN} 이다 — 실패 한 건까지 {@code WARN} 이면 경고가 잡음이 되어 다음
 * 사람이 알림을 무시하게 된다.
 *
 * <h2>급증 알림은 세 가지에만 건다</h2>
 *
 * 티켓 원문은 "로그인 실패·계정 잠금·인가 실패(403) 이벤트... 짧은 시간에 급증하면... 알린다"
 * 라고 딱 세 가지만 지목한다. {@link SecurityEvent#AUTH_TOKEN_REJECTED}(401, 토큰 없음/무효)는
 * 만료된 액세스 토큰으로 뒤늦게 요청하는 정상적인 클라이언트 동작도 포함하므로 급증 자체가
 * 공격 신호로 보기 약하다고 판단해 알림 대상에서 뺐다 — 다만 구조화된 로그는 남기므로 나중에
 * 필요해지면 {@link SecurityAlertNotifier#recordAndMaybeAlert} 호출 한 줄만 추가하면 된다.
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
	 * 가입되지 않은 이메일로 로그인이 실패한 지점에서 남긴다 (S15P21E201-722).
	 *
	 * <h2>🔴 이것이 없으면 무차별 대입의 대부분이 안 보인다</h2>
	 * 유출된 계정 목록을 들고 하는 공격은 우리에게 <b>없는 주소를 대량으로</b> 시도한다.
	 * 맞히는 비율이 낮으니 시도 대부분이 이 경로로 들어오고, 여기에 로그가 없으면 급증 경보도
	 * 안 울린다. 한 계정을 집중적으로 두드리는 공격은 실패 횟수가 쌓여 잠금까지 가므로 잡히지만,
	 * <b>넓게 뿌리는 쪽</b>은 통째로 사각지대였다.
	 *
	 * <h2>{@code attempts} 를 안 싣는 이유</h2>
	 * 실패 횟수는 {@code local_credential} 행에 센다. 계정이 없으면 셀 행이 없다. 없는 숫자를
	 * {@code 0} 이나 {@code 1} 로 지어내면 로그를 세는 쪽이 "이 사람의 첫 실패" 로 잘못 읽는다.
	 * 그래서 그 칸을 아예 안 싣고 {@code accountExists=false} 로 <b>왜 없는지</b>를 밝힌다.
	 *
	 * <p>이메일은 여전히 해시로 남긴다 — 같은 주소를 반복 시도하는 것과 여러 주소를 뿌리는 것을
	 * 구분해야 하고, 그 구분이 이 로그의 유일한 값이다.
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

	/** 토큰이 없거나 유효하지 않아 401 이 나가는 지점에서 남긴다. {@code reasonCode} 는 {@code AuthException} 의 코드다. */
	public void tokenRejected(String reasonCode) {
		log.info("event={} remoteIp={} reason={} outcome=REJECTED", SecurityEvent.AUTH_TOKEN_REJECTED,
				resolveRemoteIp(), reasonCode);
	}

	/**
	 * 이미 쓴 갱신 표가 유예 시간 안에 다시 와서 정상 경쟁으로 처리했다 (S15P21E201-723).
	 *
	 * <p>🔴 급증 경보를 걸지 않는다. 이것은 <b>거부가 아니라 허용</b>이고, 잦아진다는 것은
	 * 공격이 아니라 클라이언트가 갱신을 중복 발사한다는 뜻이다. 그걸 무차별 대입 경보와 같은
	 * 채널로 보내면 진짜 경보가 묻힌다.
	 *
	 * <p>{@code sinceUsedMs} 는 원래 사용 시각과의 간격이다. 이 값이 유예 시간 상한에 자주
	 * 붙으면 유예를 늘려야 하는지 판단할 근거가 된다.
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
	 * 현재 요청의 클라이언트 IP.
	 *
	 * <p>{@code LocalAuthService}·{@code LoginAttemptGuard} 는 서비스 계층이라 {@code HttpServletRequest}
	 * 를 매개변수로 받지 않는다. 매개변수를 늘리려면 {@code AuthController}·{@code AuthCommands} 까지
	 * 고쳐야 하는데 둘 다 이 티켓의 수정 대상 밖이다. 대신 서블릿 요청 처리 스레드에 걸려 있는
	 * {@link RequestContextHolder} 에서 꺼낸다 — 이 메서드들은 항상 그 요청을 처리하는 바로 그
	 * 스레드에서(별도 스레드 전환 없이) 호출되므로 값이 있다.
	 *
	 * <p>요청 스레드가 아닌 곳(예: 이 클래스를 테스트에서 직접 부를 때)에서는 값이 없어 {@code "unknown"}
	 * 을 돌려준다 — 예외를 던지지 않는다.
	 */
	private String resolveRemoteIp() {
		RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
		if (!(attributes instanceof ServletRequestAttributes servletRequestAttributes)) {
			return "unknown";
		}
		HttpServletRequest request = servletRequestAttributes.getRequest();
		String forwardedFor = request.getHeader("X-Forwarded-For");
		if (forwardedFor != null && !forwardedFor.isBlank()) {
			// 🔴 배포는 리버스 프록시 뒤에 있다(AuthProperties 의 기본 도메인이 이미 그 전제다).
			//    프록시를 거치면 request.getRemoteAddr() 는 프록시 자신의 주소가 된다.
			return forwardedFor.split(",")[0].trim();
		}
		String remoteAddr = request.getRemoteAddr();
		return remoteAddr == null || remoteAddr.isBlank() ? "unknown" : remoteAddr;
	}
}
