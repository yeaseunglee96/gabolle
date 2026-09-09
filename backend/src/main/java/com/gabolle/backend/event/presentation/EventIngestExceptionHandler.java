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

	/**
	 * 이벤트 하나가 계약을 못 지켰다 — 400 (S15P21E201-735).
	 *
	 * <h3>🔴 이 처리기가 없으면 잘못된 요청이 500 으로 나간다</h3>
	 * {@code EventIngestService} 의 입구 검사(축이 요구하는 값 없음, 생산자 불일치, 발생 시각이
	 * 미래, payload 가 envelope 키를 덮어씀)는 전부 {@link IllegalArgumentException} 이다. 그리고
	 * {@code EventType.fromWireName} 은 모르는 이름에 같은 예외를 던진다. 그 예외를 번역하는
	 * advice 가 없으면 <b>전부 500 {@code INTERNAL_ERROR}</b> 가 된다.
	 *
	 * <p>전에는 그중 큰 몫을 Bean Validation 이 먼저 잡아 400 을 냈다 — {@code requestId} 가
	 * {@code @NotNull} 이었기 때문이다. -735 에서 그 필수를 <b>축이 요구하는 이벤트로 좁히면서</b>
	 * 판정이 DTO 에서 서비스로 옮겨졌다. 옮기기만 하고 번역을 안 붙였으면 "잘못 보냈다" 가
	 * "서버가 고장났다" 로 바뀌었을 것이고, 앱은 그 둘을 구분할 수 없다.
	 *
	 * <p>🔴 {@link IllegalStateException}(축이 아직 안 정해진 이벤트 종류)은 여기서 안 잡는다.
	 * 그건 클라이언트가 고칠 수 있는 것이 아니라 <b>우리 코드가 덜 된 것</b>이라 500 이 맞다.
	 */
	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ApiResponse<Void>> handleInvalidEvent(IllegalArgumentException e) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(ApiResponse.failure(new ApiError("EVENT_REJECTED", e.getMessage()), requestId()));
	}

	private String requestId() {
		return "req_" + UUID.randomUUID();
	}
}
