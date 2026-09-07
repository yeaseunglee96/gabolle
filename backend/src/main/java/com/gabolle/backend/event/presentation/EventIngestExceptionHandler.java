package com.gabolle.backend.event.presentation;

import java.util.List;
import java.util.UUID;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.gabolle.backend.common.api.ApiError;
import com.gabolle.backend.common.api.ApiResponse;

/**
 * 이벤트 수집의 오류 응답 — S15P21E201-705.
 *
 * <h2>🔴 이 파일이 없으면 403 이 500 으로 나간다</h2>
 * {@code ForeignEventSubjectException} 을 던지는 것만으로는 부족하다. 그 예외를 HTTP 로 번역하는
 * advice 가 없으면 넓은 타입을 잡는 다른 advice(또는 Spring 의 기본 처리)가 <b>500
 * {@code INTERNAL_ERROR}</b> 로 답한다. 그러면 화면은 "권한이 없다" 와 "서버가 고장났다" 를
 * 구분할 수 없다.
 *
 * <p>이 저장소는 예외 처리기 범위·순서로 같은 종류를 네 번 겪었다. 가장 최근이 2026-09-07 인데,
 * 로그인 실패의 {@code AuthException} 이 auth 쪽에 남은 캐치올에 먼저 잡혀 401 이 아니라 500 으로
 * 나갔다(`INC-AUTH-007`). 그래서 여기도 {@code assignableTypes} 로 범위를 좁히고 {@code @Order} 를
 * 명시한다 — 둘 다 없으면 다른 advice 와 순서가 뒤엉킨다.
 */
@RestControllerAdvice(assignableTypes = EventIngestController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class EventIngestExceptionHandler {

	/**
	 * 본문의 {@code userId} 가 인증 주체와 다르다 — 403.
	 *
	 * <p>404 로 감추지 않는 이유: 여기서 숨길 존재 사실이 없다. 요청자는 자기 신원을 알고 있고,
	 * 거부의 이유가 "그 사람이 아니다" 라는 것 하나뿐이다. 오히려 403 으로 분명히 답하는 편이
	 * 잘못된 ID 를 보내는 클라이언트 버그를 드러낸다.
	 */
	@ExceptionHandler(EventIngestController.ForeignEventSubjectException.class)
	public ResponseEntity<ApiResponse<Void>> handleForeignSubject(
			EventIngestController.ForeignEventSubjectException e) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
				.body(ApiResponse.failure(
						new ApiError("EVENT_SUBJECT_MISMATCH", e.getMessage(), List.of("userId")),
						requestId()));
	}

	private String requestId() {
		return "req_" + UUID.randomUUID();
	}
}
