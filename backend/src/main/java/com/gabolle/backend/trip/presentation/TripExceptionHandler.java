package com.gabolle.backend.trip.presentation;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.gabolle.backend.common.api.ApiError;
import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.trip.application.TripDeletionService;
import com.gabolle.backend.trip.application.TripQueryService;
import com.gabolle.backend.trip.domain.TimeWindows;
import com.gabolle.backend.trip.domain.TripConditionRules;
import com.gabolle.backend.trip.domain.TravelModes;
import com.gabolle.backend.trip.domain.TripConstraint;
import com.gabolle.backend.trip.domain.TripRepository;

/**
 * 여행 생성·조회의 오류를 HTTP 로 번역한다. 도메인은 HTTP 를 모른다 — 그래야 같은 규칙을
 * 배치나 다른 진입점에서도 쓴다.
 */
@RestControllerAdvice(assignableTypes = TripController.class)
public class TripExceptionHandler {

    /** 어느 항목이 빠졌는지를 응답에 담는다 — "잘못된 요청" 만 돌려주지 않는다. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        List<String> fields = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .toList();

        return ResponseEntity.badRequest().body(ApiResponse.failure(
                new ApiError("TRIP_VALIDATION_FAILED", "입력한 조건 중 서버가 받지 못한 것이 있어요.", fields),
                requestId()));
    }

    /**
     * 여행 조건 재검증이 거부한 것. Spring 이 가장 구체적인 타입을 고르므로 아래
     * {@link #handleIllegalArgument} 보다 먼저 잡힌다 — 갈라 둔 이유는 어긴 항목을 전부
     * 담기 위해서다. 저쪽은 메시지 한 줄뿐이라 화면이 어느 칸을 짚을지 알 수 없다.
     */
    @ExceptionHandler(TripConditionRules.TripConditionRejectedException.class)
    public ResponseEntity<ApiResponse<Void>> handleTripConditionRejected(
            TripConditionRules.TripConditionRejectedException e) {

        return ResponseEntity.badRequest().body(ApiResponse.failure(
                new ApiError("TRIP_VALIDATION_FAILED", "입력한 조건 중 서버가 받지 못한 것이 있어요.", e.fieldLines()),
                requestId()));
    }

    /** 도메인 생성자가 거부한 것 — 종료일이 시작일보다 앞, 인원 0명 등. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(ApiResponse.failure(
                new ApiError("TRIP_VALIDATION_FAILED", "입력한 조건 중 서버가 받지 못한 것이 있어요.",
                        List.of(e.getMessage())),
                requestId()));
    }

    /**
     * timeWindow 가 {@code HH:mm-HH:mm} 모양인데 값이 틀렸다.
     * {@link IllegalArgumentException} 의 하위 타입이라 위 {@link #handleIllegalArgument}
     * 보다 먼저 잡힌다.
     */
    @ExceptionHandler(TimeWindows.InvalidTimeWindowException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidTimeWindow(TimeWindows.InvalidTimeWindowException e) {
        return ResponseEntity.badRequest().body(ApiResponse.failure(
                new ApiError("INVALID_TIME_WINDOW",
                        "하루 활동 시간대는 HH:mm-HH:mm 형식이고 끝이 시작보다 뒤여야 해요.",
                        List.of("timeWindow")),
                requestId()));
    }

    /** transport 취향 값이 {@code WALK/CAR/TRANSIT} 셋 밖이다. */
    @ExceptionHandler(TravelModes.UnsupportedTravelModeException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnsupportedTravelMode(
            TravelModes.UnsupportedTravelModeException e) {
        return ResponseEntity.badRequest().body(ApiResponse.failure(
                new ApiError("UNSUPPORTED_TRAVEL_MODE", "지원하지 않는 이동수단이에요.",
                        List.of("preferences.transport", "value=" + e.code())),
                requestId()));
    }

    /** 민감 제약은 받지 않고 거절한다 — 평문으로 한 번 저장하면 그 데이터가 남는다. */
    @ExceptionHandler(TripConstraint.SensitiveConstraintNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleSensitive(
            TripConstraint.SensitiveConstraintNotSupportedException e) {
        return ResponseEntity.badRequest().body(ApiResponse.failure(
                new ApiError("SENSITIVE_CONSTRAINT_NOT_SUPPORTED",
                        "건강·신념처럼 민감한 조건은 아직 받지 않아요.", List.of(e.type())),
                requestId()));
    }

    /** 같은 Idempotency-Key 를 다른 본문으로 재사용하면 409 다. */
    @ExceptionHandler(TripRepository.IdempotencyKeyConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleIdempotencyConflict(
            TripRepository.IdempotencyKeyConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.failure(
                new ApiError("IDEMPOTENCY_KEY_CONFLICT", "같은 요청이 다른 내용으로 다시 왔어요. 잠시 후 다시 시도해 주세요."),
                requestId()));
    }

    /** 없는 여행이거나, 요청자가 그 여행의 회원이 아니다. 둘을 구분해 응답하지 않는다. */
    @ExceptionHandler(TripQueryService.TripNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(TripQueryService.TripNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.failure(
                new ApiError("TRIP_NOT_FOUND", "그 여행을 찾지 못했어요."),
                requestId()));
    }

    /**
     * 회원이지만 소유자가 아니라서 여행을 지울 수 없다. 404 가 아니라 403 이다 — 여기까지
     * 오는 사람은 이미 그 여행을 보고 있는 동행자라 감출 것이 없다.
     *
     * <p>오류 코드는 {@code TripCollaborationExceptionHandler} 와 같은
     * {@code TRIP_FORBIDDEN} 이다. 화면이 "소유자만 할 수 있는 일" 을 한 가지로 다루게 한다.
     */
    @ExceptionHandler(TripDeletionService.TripDeleteForbiddenException.class)
    public ResponseEntity<ApiResponse<Void>> handleDeleteForbidden(
            TripDeletionService.TripDeleteForbiddenException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.failure(
                new ApiError("TRIP_FORBIDDEN", e.getMessage()),
                requestId()));
    }

    private String requestId() {
        return "req_" + UUID.randomUUID();
    }
}
