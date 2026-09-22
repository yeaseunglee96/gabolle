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
 * {@link AuthException} 을 어느 컨트롤러에서 던져도 401/403 등으로 번역한다. 여러 도메인 컨트롤러가
 * {@link AuthenticatedUsers} 를 통해 그 예외를 간접적으로 쓰므로 스코프가 없어야 한다 —
 * {@code assignableTypes} 를 주면 auth 패키지 밖 컨트롤러의 인증 실패가 500 으로 샌다.
 *
 * <p>여기에 캐치올({@code Exception}·{@code RuntimeException})을 두지 않는다. 전역 advice 에 넓은
 * 타입을 걸면 다른 도메인의 더 구체적인 핸들러가 시도되지도 못한다.
 *
 * <p>{@code @Order(HIGHEST_PRECEDENCE)} 를 빼면 운영에서 500 이 난다. Spring 은 advice 단위로 순서대로
 * 훑어 "처리할 메서드가 있는 첫 advice" 에서 멈추지, advice 들 사이에서 가장 구체적인 타입을 고르지
 * 않는다. {@code AuthExceptionHandler} 가 아직 {@code Exception} 캐치올을 갖고 있어서, 순서를 안 정하면
 * {@code AuthException} 이 그쪽에 먼저 잡힌다.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalAuthExceptionHandler {

	/**
	 * 이 코드들은 던지는 지점에서 이미 로깅된다. 여기서 상태 코드만 보고 또 남기면 같은 로그인 실패
	 * 한 건이 {@code AUTH_LOGIN_FAILURE} 와 {@code AUTH_TOKEN_REJECTED} 두 사건으로 중복 집계되고,
	 * 후자는 의미도 틀리다. 이 목록에 코드를 더하려면 던지는 지점의 로깅을 먼저 확인해야 한다.
	 *
	 * <p>401·403 이 아닌 코드는 이 방식으로 안 남는다. 새 코드를 더할 때 그것이 공격 신호라면
	 * 던지는 지점에 직접 배선한다.
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
	 * 상태 코드로 사건 종류를 정한다 — 401 은 {@link SecurityEvent#AUTH_TOKEN_REJECTED}, 403 은
	 * {@link SecurityEvent#AUTHZ_DENIED}.
	 *
	 * <p>여기로 오는 403 이 전부 자원 소유권 실패는 아니다 — 계정 상태 때문에 막힌 것도 섞여 있다.
	 * 완벽한 분류가 아니라 근사치다.
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
