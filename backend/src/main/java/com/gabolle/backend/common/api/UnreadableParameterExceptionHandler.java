package com.gabolle.backend.common.api;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.tomcat.util.http.InvalidParameterException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.gabolle.backend.place.service.RequestLanguage;

/**
 * 서블릿 컨테이너(톰캣)가 요청 매개변수를 못 읽은 요청도 우리 오류 봉투로 답하고, 기록은 WARN 한 줄만 남긴다
 * (S15P21E201-1762). 상태 코드는 톰캣이 정한 값 그대로다 — 글자 풀기 실패는 400 이다.
 *
 * <p>🔴 왜. 2026-09-26 운영에 한글 검색어를 CP949({@code %C7%D8…})로 인코딩한 요청 12건이 왔다. 컨트롤러가 검색어를 인자로
 * 읽는 순간 톰캣이 {@link InvalidParameterException} 을 던지는데, 잡는 처리기가 없어 스프링 밖으로 빠졌다. 그러면 톰캣의
 * 서블릿 감싸개가 「Servlet.service() … threw exception」을 스택째 ERROR 로 찍고 400 으로 바꾼다 — 잘못 만든 요청 하나가
 * 경보를 흐린다. 본문도 스프링 부트 기본 본문이었다({@link NoRouteExceptionHandler} 가 404·405 에서 막은 것과 같은 구멍).
 *
 * <p>로그 수준 설정으로 그 감싸개를 누르지 않는다 — 진짜 500 까지 가린다. 그래서 이 타입 하나만 잡는다. 매개변수 값은 기록에
 * 남기지 않는다 — 풀지 못한 글자라 읽을 수도 없고, 사용자가 친 검색어다. 순서를 맨 앞에 두는 까닭은
 * {@link NoRouteExceptionHandler} 와 같다.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UnreadableParameterExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(UnreadableParameterExceptionHandler.class);

	static final String INVALID_REQUEST = "INVALID_REQUEST";

	static final String MESSAGE_KO = "요청을 읽지 못했어요. 다시 시도해 주세요.";

	static final String MESSAGE_EN = "We couldn't read the request. Please try again.";

	@ExceptionHandler(InvalidParameterException.class)
	public ResponseEntity<ApiResponse<Void>> unreadable(InvalidParameterException exception,
			HttpServletRequest request) {
		HttpStatus status = HttpStatus.resolve(exception.getErrorCode());
		if (status == null || !status.is4xxClientError()) {
			status = HttpStatus.BAD_REQUEST;
		}
		Throwable cause = exception.getCause();
		log.warn("요청 매개변수를 읽지 못했다 — {} 로 답한다. path={} cause={}", status.value(), request.getRequestURI(),
				(cause == null) ? "-" : cause.getClass().getSimpleName());
		String message = RequestLanguage.prefersEnglish(request.getHeader(HttpHeaders.ACCEPT_LANGUAGE)) ? MESSAGE_EN
				: MESSAGE_KO;
		String requestId = request.getHeader("X-Request-Id");
		return ResponseEntity.status(status).body(ApiResponse.failure(new ApiError(INVALID_REQUEST, message),
				(requestId == null || requestId.isBlank()) ? UUID.randomUUID().toString() : requestId));
	}
}
