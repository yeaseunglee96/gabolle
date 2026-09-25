package com.gabolle.backend.common.api;

import java.util.Set;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.gabolle.backend.place.service.RequestLanguage;

/**
 * 없는 주소(404)·없는 메서드(405)도 우리 오류 봉투로 답한다 (S15P21E201-1675). 상태 코드는 그대로다.
 *
 * <p>🔴 왜. 이 둘은 컨트롤러에 닿기 전에 스프링이 정하는 오류라 도메인 핸들러가 못 잡고, 스프링 부트 기본 오류 본문
 * {@code {"timestamp","status","error":"Not Found","path"}} 이 나갔다 — {@code message} 칸이 없고 {@code error} 가 영어
 * 글자다. 앱이 그것을 그대로 보이면 「Not Found」가 화면에 뜬다. 「모든 오류는 같은 봉투」 약속을 여기서도 지킨다.
 *
 * <p>로그인 안 한 요청은 대개 여기까지 안 온다 — 보안 필터가 먼저 401 봉투로 답한다. 영어 문장은 {@code Accept-Language}
 * 의 첫 언어가 영어일 때다 — 장소 상세와 같은 규칙({@link RequestLanguage#prefersEnglish}). 도메인 오류는 아직 한국어뿐이다.
 *
 * <p>좁은 타입만 건다. 전역 advice 에 넓은 타입({@code Exception})을 걸면 다른 도메인의 더 구체적인 핸들러가 시도되지도
 * 못한다. 순서를 맨 앞에 두는 것은 누가 나중에 넓은 타입의 전역 advice 를 더해도 이 둘이 그쪽에 먼저 잡히지 않게 하려는
 * 것이다({@code GlobalAuthExceptionHandler} 와 같은 까닭, 잡는 타입은 겹치지 않는다).
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class NoRouteExceptionHandler {

	static final String NOT_FOUND = "NOT_FOUND";

	static final String METHOD_NOT_ALLOWED = "METHOD_NOT_ALLOWED";

	static final String MESSAGE_KO = "지금은 이 기능을 쓸 수 없어요. 잠시 뒤 다시 시도해 주세요.";

	static final String MESSAGE_EN = "This feature isn't available right now. Please try again later.";

	@ExceptionHandler({ NoResourceFoundException.class, NoHandlerFoundException.class })
	public ResponseEntity<ApiResponse<Void>> notFound(Exception exception, HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(failure(NOT_FOUND, request));
	}

	/** 스프링 기본처럼 {@code Allow} 머리에 되는 메서드를 싣는다. */
	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ApiResponse<Void>> methodNotAllowed(HttpRequestMethodNotSupportedException exception,
			HttpServletRequest request) {
		ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);
		Set<HttpMethod> supported = exception.getSupportedHttpMethods();
		if (supported != null && !supported.isEmpty()) {
			response.allow(supported.toArray(HttpMethod[]::new));
		}
		return response.body(failure(METHOD_NOT_ALLOWED, request));
	}

	private static ApiResponse<Void> failure(String code, HttpServletRequest request) {
		String message = RequestLanguage.prefersEnglish(request.getHeader(HttpHeaders.ACCEPT_LANGUAGE)) ? MESSAGE_EN
				: MESSAGE_KO;
		String requestId = request.getHeader("X-Request-Id");
		return ApiResponse.failure(new ApiError(code, message),
				(requestId == null || requestId.isBlank()) ? UUID.randomUUID().toString() : requestId);
	}
}
