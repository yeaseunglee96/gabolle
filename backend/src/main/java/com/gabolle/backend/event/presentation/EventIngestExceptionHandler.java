package com.gabolle.backend.event.presentation;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.gabolle.backend.common.api.ApiError;
import com.gabolle.backend.common.api.ApiResponse;

/**
 * 이벤트 수집의 오류 응답. 이 advice 가 없으면 넓은 타입을 잡는 다른 advice 가 먼저 잡아
 * 500 으로 나가고, 화면은 "권한이 없다" 와 "서버가 고장났다" 를 구분할 수 없다.
 *
 * <p>{@code assignableTypes} 와 {@code @Order} 는 둘 다 필요하다 — 범위를 안 좁히면 다른
 * 컨트롤러의 예외까지 가로채고, 좁혀도 순서가 없으면 다른 넓은 advice 에 먼저 잡힌다.
 */
@RestControllerAdvice(assignableTypes = EventIngestController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class EventIngestExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(EventIngestExceptionHandler.class);

	/**
	 * 본문의 {@code userId} 가 인증 주체와 다르다 — 403. 404 로 감추지 않는다. 여기서 숨길
	 * 존재 사실이 없고, 403 이라야 잘못된 ID 를 보내는 클라이언트 버그가 드러난다.
	 */
	@ExceptionHandler(EventIngestController.ForeignEventSubjectException.class)
	public ResponseEntity<ApiResponse<Void>> handleForeignSubject(
			EventIngestController.ForeignEventSubjectException e) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
				.body(ApiResponse.failure(
						new ApiError("EVENT_SUBJECT_MISMATCH", e.getMessage(), List.of("userId")),
						requestId()));
	}

	/**
	 * 이벤트 하나가 계약을 못 지켰다 — 400. {@code EventIngestService} 의 입구 검사와
	 * {@code EventType.fromWireName} 이 전부 {@link IllegalArgumentException} 을 던지므로,
	 * 번역하지 않으면 잘못된 요청이 500 으로 나간다.
	 *
	 * <p>{@link IllegalStateException}(축이 아직 안 정해진 이벤트 종류)은 일부러 여기서 안 잡는다.
	 * 클라이언트가 고칠 수 있는 것이 아니라 우리 코드가 덜 된 것이라 500 이 맞다.
	 *
	 * <p>🔴 거절을 서버 기록에 남긴다 (S15P21E201-1730). 앱은 거절돼도 다시 보내지 않고 조용히 버리므로, 여기에 안
	 * 남기면 어떤 이벤트가 왜 안 쌓이는지 아무도 모른다 — 2026-09-26 운영에서 400 6건의 이유를 끝내 알 수 없었다.
	 * 문구에는 이벤트 종류 · 칸 이름 · 시각만 들어가고 사람 번호나 payload 값은 안 들어간다.
	 */
	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ApiResponse<Void>> handleInvalidEvent(IllegalArgumentException e) {
		log.warn("앱 이벤트를 거절했다(400 EVENT_REJECTED) — {}", e.getMessage());
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(ApiResponse.failure(new ApiError("EVENT_REJECTED", e.getMessage()), requestId()));
	}

	private String requestId() {
		return "req_" + UUID.randomUUID();
	}
}
