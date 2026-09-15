package com.gabolle.backend.collection.presentation;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.gabolle.backend.collection.application.CollectionService;
import com.gabolle.backend.common.api.ApiError;
import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.place.service.PlaceNotFoundException;

/**
 * 컬렉션의 오류를 HTTP 로 번역한다 — S15P21E201-1013.
 *
 * <p>{@code RecommendationJobExceptionHandler} 와 같은 패턴이다(개발계획서 4.2 «세밀한 예외
 * 계층을 두지 않는다»).
 */
@RestControllerAdvice(assignableTypes = CollectionController.class)
public class CollectionExceptionHandler {

	/**
	 * 없는 컬렉션이거나, 있어도 내 것이 아니다.
	 *
	 * <p>🔴 <b>둘을 구분해 답하지 않는다.</b> 「남의 것이다」로 답하면 그 번호의 컬렉션이
	 * 존재한다는 사실이 새어 나간다 — 이 저장소가 여행·추천에서 쓰는 것과 같은 규칙이다.
	 */
	@ExceptionHandler(CollectionService.CollectionNotFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handleNotFound(CollectionService.CollectionNotFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.failure(
				new ApiError("COLLECTION_NOT_FOUND", "컬렉션을 찾을 수 없어요."), requestId()));
	}

	/**
	 * 없는 장소를 담으려 했다.
	 *
	 * <p>🔴 이것을 안 막으면 외래키 위반이 그대로 올라와 <b>500</b> 이 나가고, 화면은
	 * 「서버가 고장났다」와 「그런 장소가 없다」를 구분하지 못한다.
	 */
	@ExceptionHandler(PlaceNotFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handlePlaceNotFound(PlaceNotFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.failure(
				new ApiError("PLACE_NOT_FOUND", "장소를 찾을 수 없어요."), requestId()));
	}

	/**
	 * 이름이 비었거나, {@code kind} 가 없거나, 종류에 맞지 않는 칸을 고치려 했다.
	 *
	 * <p>🔴 {@code message} 는 번역 키가 아니라 <b>사람이 읽는 문장</b>이어야 한다 — 프론트가
	 * 이 값을 그대로 화면에 띄우는 것이 이 저장소에서 이미 실측됐다
	 * ({@code RecommendationJobExceptionHandler} 의 같은 자리 참고). 자세한 원인은
	 * {@code fields} 에만 싣는다.
	 */
	@ExceptionHandler({ IllegalArgumentException.class, IllegalStateException.class })
	public ResponseEntity<ApiResponse<Void>> handleInvalid(RuntimeException e) {
		return ResponseEntity.badRequest().body(ApiResponse.failure(
				new ApiError("COLLECTION_VALIDATION_FAILED", "입력한 내용을 확인해 주세요.",
						List.of(e.getMessage())),
				requestId()));
	}

	private String requestId() {
		return "req_" + UUID.randomUUID();
	}
}
