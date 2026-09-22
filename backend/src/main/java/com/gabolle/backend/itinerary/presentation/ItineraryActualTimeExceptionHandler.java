package com.gabolle.backend.itinerary.presentation;

import java.util.List;
import java.util.UUID;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.gabolle.backend.common.api.ApiError;
import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.itinerary.application.ItineraryAccess;
import com.gabolle.backend.itinerary.domain.ItineraryItemActual;
import com.gabolle.backend.itinerary.domain.ItineraryRevision;

/**
 * {@link ItineraryActualTimeController} 전용 오류 번역기.
 * {@code assignableTypes} 로 이 컨트롤러 하나만 본다. 범위 없는 advice 가 남의 예외를 가로챈
 * 사고가 이미 있었고, {@code @Order} 가 없으면 승자가 컴포넌트 스캔 순서로 정해진다.
 * 거부 응답의 코드는 편집 경로({@link ItineraryExceptionHandler})와 같은 이름을 쓴다. 같은
 * 상황에 다른 코드를 주면 앱이 경로마다 다른 분기를 만들어야 한다.
 * {@code fields} 는 {@code "이름=값"} 문자열이다 — 그 형식이 앱과의 계약인 이유는
 * {@link ItineraryExceptionHandler} 클래스 주석에 있다.
 */
@RestControllerAdvice(assignableTypes = ItineraryActualTimeController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ItineraryActualTimeExceptionHandler {

	/** 없는 일정이거나, 있어도 요청자가 그 일정이 속한 여행의 회원이 아니다 — 둘 다 404. */
	@ExceptionHandler(ItineraryQueryController.ItineraryNotFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handleNotFound(
			ItineraryQueryController.ItineraryNotFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(ApiResponse.failure(new ApiError("ITINERARY_NOT_FOUND", "일정을 찾을 수 없습니다."), requestId()));
	}

	/**
	 * 회원이지만 VIEWER 라 이 일정에 기록을 남길 수 없다 — 403. 비회원의 404 와 다른 코드다 —
	 * 존재를 감출 이유가 없는 사람에게 404 를 주면 화면이 "방금 보던 일정이 사라졌다" 로 읽는다.
	 */
	@ExceptionHandler(ItineraryAccess.ItineraryForbiddenException.class)
	public ResponseEntity<ApiResponse<Void>> handleForbidden(ItineraryAccess.ItineraryForbiddenException e) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
				.body(ApiResponse.failure(
						new ApiError("ITINERARY_FORBIDDEN", e.getMessage(), List.of("role=" + e.role())),
						requestId()));
	}

	/** 최신 판에 그 방문지가 없다 — 404. 편집 경로와 같은 코드를 쓴다. */
	@ExceptionHandler(ItineraryRevision.ItemNotFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handleItemNotFound(ItineraryRevision.ItemNotFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(ApiResponse.failure(
						new ApiError("ITINERARY_ITEM_NOT_FOUND", "그 일정 항목을 찾을 수 없습니다.",
								List.of("itemKey=" + e.itemKey())),
						requestId()));
	}

	/** 도착도 출발도 없다 — 400. 어느 칸이 비었는지 둘 다 실어 준다. */
	@ExceptionHandler(ItineraryItemActual.NoTimeGivenException.class)
	public ResponseEntity<ApiResponse<Void>> handleNoTimeGiven(ItineraryItemActual.NoTimeGivenException e) {
		return ResponseEntity.badRequest()
				.body(ApiResponse.failure(
						new ApiError("INVALID_REQUEST", e.getMessage(),
								List.of("arrivedAt=null", "departedAt=null")),
						requestId()));
	}

	/** 출발이 도착보다 앞선다 — 400. 두 값을 함께 실어 화면이 어느 쪽을 고칠지 보여줄 수 있게 한다. */
	@ExceptionHandler(ItineraryItemActual.DepartedBeforeArrivedException.class)
	public ResponseEntity<ApiResponse<Void>> handleDepartedBeforeArrived(
			ItineraryItemActual.DepartedBeforeArrivedException e) {
		return ResponseEntity.badRequest()
				.body(ApiResponse.failure(
						new ApiError("INVALID_REQUEST", e.getMessage(),
								List.of("arrivedAt=" + e.arrivedAt(), "departedAt=" + e.departedAt())),
						requestId()));
	}

	/**
	 * 본문 JSON 을 읽을 수 없다 — 400. 시각 형식이 ISO-8601 이 아닌 경우가 여기로 온다.
	 * 파싱 오류 원문은 응답에 싣지 않는다(내부 구조가 새어 나간다).
	 */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException e) {
		return ResponseEntity.badRequest()
				.body(ApiResponse.failure(
						new ApiError("INVALID_REQUEST", "요청 본문을 읽을 수 없습니다. 시각은 시간대를 포함한 "
								+ "ISO-8601 형식으로 보내십시오.", List.of("arrivedAt", "departedAt")),
						requestId()));
	}

	private String requestId() {
		return "req_" + UUID.randomUUID();
	}
}
