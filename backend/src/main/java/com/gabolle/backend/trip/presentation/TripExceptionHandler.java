package com.gabolle.backend.trip.presentation;

import com.gabolle.backend.common.api.ApiError;
import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.trip.domain.TripConstraint;
import com.gabolle.backend.trip.domain.TripRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 여행 생성의 오류를 HTTP 로 번역한다.
 *
 * <p>🔴 도메인은 HTTP 를 모른다. 그래야 같은 규칙을 배치나 다른 진입점에서도 쓴다.
 *
 * <p>개발계획서 4.2 — "하지 않는 일: 세밀한 예외 계층. enum 오류 코드만 지킨다."
 * 그래서 이 처리기는 얇다.
 */
@RestControllerAdvice(assignableTypes = TripController.class)
public class TripExceptionHandler {

    /**
     * 🔴 티켓 완료 기준 — "필수 항목이 빠진 요청은 거부되고, <b>어느 항목이 빠졌는지</b>가
     * 응답에 들어 있다." 그래서 필드 이름을 담는다. "잘못된 요청" 만 돌려주지 않는다.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        List<String> fields = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .toList();

        return ResponseEntity.badRequest().body(ApiResponse.failure(
                new ApiError("TRIP_VALIDATION_FAILED", "error.trip.validation", fields),
                requestId()));
    }

    /** 도메인 생성자가 거부한 것 — 종료일이 시작일보다 앞, 인원 0명 등. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(ApiResponse.failure(
                new ApiError("TRIP_VALIDATION_FAILED", "error.trip.validation",
                        List.of(e.getMessage())),
                requestId()));
    }

    /**
     * 🔴 민감 제약을 M1 에서 받으려 했을 때.
     *
     * <p>"나중에 처리" 로 넘기지 않는다. 평문으로 한 번 저장하면 그 데이터가 남는다.
     */
    @ExceptionHandler(TripConstraint.SensitiveConstraintNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleSensitive(
            TripConstraint.SensitiveConstraintNotSupportedException e) {
        return ResponseEntity.badRequest().body(ApiResponse.failure(
                new ApiError("SENSITIVE_CONSTRAINT_NOT_SUPPORTED",
                        "error.trip.sensitiveConstraint", List.of(e.type())),
                requestId()));
    }

    /** 🔴 API-09 — 같은 Idempotency-Key 를 다른 본문으로 재사용하면 409 다. */
    @ExceptionHandler(TripRepository.IdempotencyKeyConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleIdempotencyConflict(
            TripRepository.IdempotencyKeyConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.failure(
                new ApiError("IDEMPOTENCY_KEY_CONFLICT", "error.idempotency.conflict"),
                requestId()));
    }

    private String requestId() {
        return "req_" + UUID.randomUUID();
    }
}
