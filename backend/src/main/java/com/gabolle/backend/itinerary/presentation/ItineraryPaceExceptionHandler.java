package com.gabolle.backend.itinerary.presentation;

import java.util.UUID;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.gabolle.backend.common.api.ApiError;
import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.itinerary.application.ItineraryPaceService;

/**
 * {@link ItineraryPaceController} 전용 오류 번역기 — S15P21E201-96 · -304 · -308.
 *
 * <p>{@code ItineraryQueryExceptionHandler} 의 javadoc 이 남긴 실측대로, 범위 없는 advice 가
 * 남의 예외를 가로챈 사고가 있었다. 그래서 {@code assignableTypes} 로 이 컨트롤러 하나만 좁히고
 * 이 두 경로가 실제로 던지는 것만 다룬다.
 *
 * <p>{@code message} 는 한국어 문장이다 — 프론트가 {@code error.message} 를 그대로 화면에
 * 띄운다.
 */
@RestControllerAdvice(assignableTypes = ItineraryPaceController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ItineraryPaceExceptionHandler {

    /** 없는 일정이거나 요청자가 그 일정이 속한 여행의 회원이 아니다 — 둘 다 404. */
    @ExceptionHandler(ItineraryQueryController.ItineraryNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(
            ItineraryQueryController.ItineraryNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.failure(new ApiError("ITINERARY_NOT_FOUND", "일정을 찾을 수 없습니다."), requestId()));
    }

    /** dayIndex 가 여행 기간을 벗어났다 — 400. */
    @ExceptionHandler(ItineraryPaceService.DayOutsideTripException.class)
    public ResponseEntity<ApiResponse<Void>> handleDayOutsideTrip(
            ItineraryPaceService.DayOutsideTripException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure(new ApiError("DAY_OUTSIDE_TRIP", e.getMessage()), requestId()));
    }

    private String requestId() {
        return "req_" + UUID.randomUUID();
    }
}
