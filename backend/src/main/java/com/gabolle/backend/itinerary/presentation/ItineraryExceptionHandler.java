package com.gabolle.backend.itinerary.presentation;

import com.gabolle.backend.itinerary.domain.StaleItineraryVersionException;
import com.gabolle.backend.itinerary.presentation.dto.ItineraryConflictResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 일정 편집 충돌을 409 로 바꾼다.
 *
 * <p>🔴 도메인 예외를 HTTP 로 번역하는 것은 <b>표현 계층의 일</b>이다.
 * 도메인 계층은 HTTP 를 모른다 — {@link StaleItineraryVersionException} 에는
 * {@code 409} 라는 숫자가 없다. 그래야 같은 규칙을 배치나 다른 진입점에서도 쓸 수 있다.
 *
 * <p>개발계획서 4.2 — "하지 않는 일: 세밀한 예외 계층.
 * GB-API-001 5장의 enum 오류 코드만 지킨다." 그래서 이 처리기는 얇다.
 */
@RestControllerAdvice
public class ItineraryExceptionHandler {

    @ExceptionHandler(StaleItineraryVersionException.class)
    public ResponseEntity<ItineraryConflictResponse> handleStaleVersion(
            StaleItineraryVersionException e) {

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ItineraryConflictResponse.of(
                        e.itineraryId(), e.attemptedBaseVersion(), e.latestVersion()));
    }
}
