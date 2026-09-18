package com.gabolle.backend.dish.presentation;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.gabolle.backend.common.api.ApiError;
import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.dish.adapter.GmsDishDescriber;
import com.gabolle.backend.dish.application.DishImageRateLimiter;
import com.gabolle.backend.dish.application.DishService;

/**
 * 음식 설명·그림의 실패를 HTTP 로 번역한다 — S15P21E201-1272.
 *
 * <h2>🔴 이쪽 실패는 메뉴판 읽기의 실패와 무게가 다르다</h2>
 *
 * 메뉴판 읽기가 실패하면 <b>알레르기 낱말을 못 본다</b> — 사람이 다칠 수 있어서 그쪽은
 * 어떤 실패도 빈 결과로 바꾸지 않는다. 설명과 그림은 <b>있으면 좋은 것</b>이다. 그래서
 * 여기서 실패해도 화면은 이미 받은 글자·가격·알레르기 낱말을 그대로 들고 있어야 하고,
 * 이 실패는 <b>그 아래 한 칸에만</b> 그려져야 한다.
 *
 * <p>그 약속을 코드로 지키는 자리가 <b>응답 코드</b>다. 여기서 나가는 것은 전부 이
 * 경로의 실패이고, 메뉴판 응답을 되돌리지 않는다.
 */
@RestControllerAdvice(assignableTypes = DishController.class)
public class DishExceptionHandler {

	/**
	 * 그림 한도를 넘었다.
	 *
	 * <p>🔴 <b>메뉴판 읽기 한도와 다른 코드를 쓴다.</b> 같은 코드를 쓰면 화면이
	 * 「오늘 메뉴판을 다 썼어요」로 그리는데, 그건 거짓이다 — 읽기는 아직 남아 있다.
	 */
	@ExceptionHandler(DishImageRateLimiter.TooManyDishImagesException.class)
	public ResponseEntity<ApiResponse<Void>> handleTooMany(
			DishImageRateLimiter.TooManyDishImagesException e) {
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiResponse.failure(
				new ApiError("DISH_IMAGE_RATE_LIMITED", e.getMessage()), requestId()));
	}

	/** 설정이 없어 지금은 못 물어본다. 🔴 「모델이 모르는 음식」이 아니다. */
	@ExceptionHandler(DishService.DishUnavailableException.class)
	public ResponseEntity<ApiResponse<Void>> handleUnavailable(DishService.DishUnavailableException e) {
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiResponse.failure(
				new ApiError("DISH_UNAVAILABLE", "음식 설명을 지금은 쓸 수 없어요. 잠시 뒤 다시 시도해 주세요."),
				requestId()));
	}

	/** 모델 쪽이 어긋났다. */
	@ExceptionHandler(GmsDishDescriber.DishDescribeFailedException.class)
	public ResponseEntity<ApiResponse<Void>> handleDescribeFailed(
			GmsDishDescriber.DishDescribeFailedException e) {
		return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ApiResponse.failure(
				new ApiError("DISH_DESCRIBE_FAILED", "이 음식 설명을 가져오지 못했어요."), requestId()));
	}

	/**
	 * 아직 만드는 중이다.
	 *
	 * <p>🔴 <b>202 다. 404 로 답하지 않는다.</b> 404 면 화면은 「그림이 없는 음식」으로
	 * 보고 그만 물어본다 — 10초만 더 기다리면 오는 그림을 영영 안 받는다.
	 */
	@ExceptionHandler(DishService.DishImageNotReadyException.class)
	public ResponseEntity<Void> handleNotReady(DishService.DishImageNotReadyException e) {
		return ResponseEntity.accepted().build();
	}

	/** 그런 그림이 없거나 못 만들었다 — 화면은 그만 물어본다. */
	@ExceptionHandler(DishService.DishImageNotFoundException.class)
	public ResponseEntity<Void> handleNotFound(DishService.DishImageNotFoundException e) {
		return ResponseEntity.notFound().build();
	}

	/** 이름이 비었다. */
	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ApiResponse<Void>> handleInvalid(IllegalArgumentException e) {
		return ResponseEntity.badRequest().body(ApiResponse.failure(
				new ApiError("DISH_INVALID_NAME", e.getMessage()), requestId()));
	}

	private String requestId() {
		return "req_" + UUID.randomUUID();
	}
}
