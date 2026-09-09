package com.gabolle.backend.common.security;

import java.util.Set;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.gabolle.backend.auth.service.AuthException;
import com.gabolle.backend.common.api.ApiError;
import com.gabolle.backend.common.api.ApiResponse;

/**
 * {@link AuthException} 을 <b>어느 컨트롤러에서 던져도</b> 401/403 등으로 번역한다 — S15P21E201-294.
 *
 * <p>{@link AuthenticatedUsers}(이 클래스와 같은 패키지)가 그 예외를 던지고, 여러 도메인
 * 컨트롤러(TripController·ItineraryEditController·RecommendationJobController 등)가
 * {@code AuthenticatedUsers.requireId(authentication)} 를 통해 그것을 간접적으로 쓴다.
 * 그래서 이 advice 는 진짜로 스코프가 없어야 한다 — {@code assignableTypes} 를 주면
 * auth 패키지 밖 컨트롤러의 인증 실패가 500 으로 샌다.
 *
 * <p>🔴 <b>그래서 여기에는 캐치올을 두지 않는다.</b> {@code auth.api.AuthExceptionHandler}
 * 가 예전에 스코프 없이 {@code @ExceptionHandler(Exception.class)} 를 가졌던 것이 이번
 * 버그의 원인이었다({@code AuthExceptionHandler} 의 2026-09-07 javadoc 참고) — 전역
 * advice 에 넓은 타입(특히 {@code Exception}·{@code RuntimeException})을 걸면 다른
 * 도메인의 더 구체적인 핸들러가 시도되지도 못한다. 이 클래스는 딱 {@link AuthException}
 * 하나만 잡는다.
 *
 * <p>🔴 <b>2026-09-07 — {@code @Order(HIGHEST_PRECEDENCE)} 를 붙였다. 이것이 없으면 운영에서 500 이 난다.</b>
 * {@code AuthExceptionHandler} 는 이제 {@code assignableTypes} 로 좁혀졌지만 <b>여전히
 * {@code @ExceptionHandler(Exception.class)} 캐치올을 갖고 있고</b>, {@code AuthException} 핸들러는 이 클래스로
 * 옮겨졌다. Spring 은 예외 하나를 처리할 때 <b>advice 단위로</b> 순서대로 훑어 "처리할 메서드가 있는 첫 advice"
 * 에서 멈춘다 — advice 들 사이에서 가장 구체적인 타입을 고르는 것이 아니다. 그래서 {@code AuthController} 요청의
 * {@code AuthException} 은 그쪽 캐치올({@code Exception})에 먼저 잡혀 500 {@code INTERNAL_ERROR} 가 됐다.
 * 배포에서 실측했다 — 없는 이메일로 로그인하면 401 {@code INVALID_CREDENTIALS} 가 아니라 500 이 나갔다.
 *
 * <p>순서를 명시하면 이 advice 가 언제나 먼저 시도되므로 {@code AuthException} 은 어느 컨트롤러에서 나와도
 * 제 상태 코드로 나간다. 스캔 순서에 기대지 않는 것이 요점이다 — 이 저장소는 같은 함정을 이미 세 번 만났다
 * ({@code TripExceptionHandler} 의 예외 계층, 기록 묶음의 원인 사슬, 그리고 이번 advice 순서).
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalAuthExceptionHandler {

	/**
	 * 🔴 이 코드들은 <b>여기서 다시 로깅하지 않는다.</b> {@code LocalAuthService}·{@code LoginAttemptGuard}
	 * 가 이미 던지는 지점에서 {@link SecurityEventLogger#loginFailure}·{@link SecurityEventLogger#accountLocked}
	 * 로 남긴다 — 그쪽은 이 advice 가 모르는 {@code attempts}(몇 번째 실패인지) 를 알고 있어서 정보가
	 * 더 정확하다. 여기서 상태 코드만 보고 또 로그를 남기면 같은 로그인 실패 한 건이
	 * {@code AUTH_LOGIN_FAILURE} 와 {@code AUTH_TOKEN_REJECTED} 두 사건으로 중복 집계되고, 후자는
	 * "토큰이 없거나 무효하다" 는 뜻인데 실제로는 "비밀번호가 틀렸다" 라서 의미도 틀린다.
	 *
	 * <p>🔴 <b>2026-09-07 후속 — 그 사각지대를 메웠다.</b> 이 목록이 걸러 주는 두 코드는
	 * {@code LocalAuthService} 만 던지는 것이 아니라 {@code OAuthAccountService}(소셜 연결의 비밀번호
	 * 확인)도 던진다. 그쪽에는 던지는 지점의 로깅이 없어서, 이 목록이 걸러 준 뒤 <b>어디에서도 남지
	 * 않는</b> 상태였다 — 소셜 연결 화면을 통한 비밀번호 시도가 통째로 안 보였다. 이제 그 파일도
	 * 같은 방식으로 남기므로 이 목록의 전제("던지는 지점에서 이미 남긴다")가 실제로 참이다.
	 *
	 * <p>{@code ACCOUNT_UNAVAILABLE} 은 이 목록에 없다 — 403 이라 {@link SecurityEventLogger#authzDenied}
	 * 로 남는다.
	 *
	 * <p>🔴 <b>상태 코드로 보는 이 방식이 놓치는 것이 아직 남아 있다.</b> 401·403 이 아닌 코드는
	 * 여기서 안 남는다. 그중 관측이 필요한 두 개(이미 잠긴 계정에 오는 429, 허용 목록에 없는
	 * redirect URI 로 오는 400)는 던지는 지점에 직접 배선했다 —
	 * {@link SecurityEvent#AUTH_LOCKED_ACCOUNT_ATTEMPT}·{@link SecurityEvent#AUTH_OAUTH_REDIRECT_REJECTED}.
	 * 나머지 400·409 는 대개 형식 오류라 남기면 잡음이 된다. 새 코드를 더할 때 <b>그 코드가 공격
	 * 신호인지</b>를 판단해서, 그렇다면 던지는 지점에 한 줄 넣는다.
	 */
	private static final Set<String> LOGGED_ELSEWHERE = Set.of("INVALID_CREDENTIALS", "TOO_MANY_LOGIN_ATTEMPTS");

	private final SecurityEventLogger securityEventLogger;

	public GlobalAuthExceptionHandler(SecurityEventLogger securityEventLogger) {
		this.securityEventLogger = securityEventLogger;
	}

	@ExceptionHandler(AuthException.class)
	public ResponseEntity<ApiResponse<Void>> handleAuth(AuthException exception, HttpServletRequest request) {
		logSecurityEventIfNeeded(exception);
		return ResponseEntity.status(exception.getStatus()).body(ApiResponse.failure(
				new ApiError(exception.getCode(), exception.getMessage()), requestId(request)));
	}

	/**
	 * 이 advice 를 거치는 모든 {@link AuthException} 을 상태 코드로 보고 남긴다 — 401 은
	 * {@link SecurityEvent#AUTH_TOKEN_REJECTED}(토큰 없음/무효), 403 은 {@link SecurityEvent#AUTHZ_DENIED}
	 * (인증은 됐는데 권한 없음). 로그인 전용 코드({@link #LOGGED_ELSEWHERE})는 던지는 지점에서 이미
	 * 더 정확한 정보로 로깅됐으므로 건너뛴다.
	 *
	 * <p>🔴 이 advice 로 오는 403 이 전부 "자원 소유권" 같은 좁은 의미의 인가 실패는 아니다
	 * ({@code EMAIL_NOT_VERIFIED}·{@code ACCOUNT_UNAVAILABLE} 처럼 계정 상태 때문에 막힌 것도 섞여
	 * 있다). 진짜 자원 소유권 403(trip·story·itinerary·place 패키지의 전용 핸들러)은 이 advice 를
	 * 타지 않고 이 티켓의 수정 대상 밖이라 여기서는 다루지 않는다. 그래서 이 클래스가 볼 수 있는
	 * 403 은 전부 {@code AUTHZ_DENIED} 로 남긴다 — 완벽한 분류는 아니지만 이 파일의 수정 범위 안에서
	 * 낼 수 있는 가장 가까운 근사치다.
	 */
	private void logSecurityEventIfNeeded(AuthException exception) {
		if (LOGGED_ELSEWHERE.contains(exception.getCode())) {
			return;
		}
		if (exception.getStatus() == HttpStatus.UNAUTHORIZED) {
			this.securityEventLogger.tokenRejected(exception.getCode());
		}
		else if (exception.getStatus() == HttpStatus.FORBIDDEN) {
			this.securityEventLogger.authzDenied(exception.getCode());
		}
	}

	private String requestId(HttpServletRequest request) {
		String requestId = request.getHeader("X-Request-Id");
		return requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId;
	}
}
