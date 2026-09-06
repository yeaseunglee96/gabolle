package com.gabolle.backend.itinerary.presentation;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.gabolle.backend.common.api.ApiError;
import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.itinerary.application.ItineraryAccess;
import com.gabolle.backend.itinerary.application.ItineraryEditService;
import com.gabolle.backend.itinerary.domain.ItineraryRevision;
import com.gabolle.backend.itinerary.domain.StaleItineraryVersionException;

/**
 * {@link ItineraryEditController} 전용 오류 번역기.
 *
 * <h2>🔴 2026-09-06 (S15P21E201-662) — 팀 공용 봉투로 바꿨다</h2>
 * 예전에는 손으로 만든 봉투에 {@code error.details}(객체)를 담았다. 그렇게 한 이유가
 * 주석에 적혀 있었다 — 팀 공용 {@code ApiError} 는 {@code fields: List<String>} 만 있어서
 * 구조화된 객체를 못 담는다는 것이었다. <b>그런데 앱은 실제로 그 문자열 목록을 읽는다.</b>
 * {@code frontend/src/plan/itinerary.ts} 가 {@code error.fields} 에서
 * {@code /^latestVersion=/} 로 최신 판 번호를 뽑는다. 즉 담을 자리가 없던 게 아니라
 * 읽는 쪽이 이미 정해 둔 자리를 서버가 안 쓰고 있었다. 읽는 쪽에 맞춘다.
 *
 * <p>그래서 {@code fields} 에 {@code "이름=값"} 문자열을 넣는다. 형식이 계약이므로
 * 이름을 바꾸면 앱이 못 읽는다 — 그 사실을 회귀 테스트가 지킨다.
 *
 * <p>🔴 {@code assignableTypes} 로 이 컨트롤러 하나만 본다. 범위 없는 advice 가 남의
 * 예외를 가로챈 사고가 이미 있었다({@code ItineraryQueryExceptionHandler}·
 * {@code PlaceExceptionHandler} 의 같은 실측). {@code @Order} 도 같은 이유다 —
 * 순서를 안 주면 승자가 컴포넌트 스캔 순서로 정해진다.
 *
 * <p>{@code message} 는 한국어 문장이다. 앱이 {@code error.message} 를 그대로 화면에
 * 띄우는 자리가 있다({@code ItineraryQueryExceptionHandler} 의 같은 실측).
 */
@RestControllerAdvice(assignableTypes = ItineraryEditController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ItineraryExceptionHandler {

	public static final String CONFLICT_CODE = "ITINERARY_VERSION_CONFLICT";

	@ExceptionHandler(StaleItineraryVersionException.class)
	public ResponseEntity<ApiResponse<Void>> handleStaleVersion(StaleItineraryVersionException e) {
		// 🔴 이 두 문자열의 모양이 앱과의 계약이다. 앱은 latestVersion= 뒤의 숫자를 정규식으로 읽는다.
		List<String> fields = List.of(
				"latestVersion=" + e.latestVersion(),
				"attemptedBaseVersion=" + e.attemptedBaseVersion());

		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(ApiResponse.failure(
						new ApiError(CONFLICT_CODE, "다른 변경이 먼저 반영됐습니다. 최신 일정을 불러와 다시 시도해 주세요.", fields),
						requestId()));
	}

	/** 바탕 판에 그 항목이 없다 — 404. */
	@ExceptionHandler(ItineraryRevision.ItemNotFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handleItemNotFound(ItineraryRevision.ItemNotFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(ApiResponse.failure(
						new ApiError("ITINERARY_ITEM_NOT_FOUND", "그 일정 항목을 찾을 수 없습니다.",
								List.of("itemId=" + e.itemKey())),
						requestId()));
	}

	/** 그런 일정이 없다 — 404. 조회와 같은 코드를 쓴다. */
	@ExceptionHandler(NoSuchElementException.class)
	public ResponseEntity<ApiResponse<Void>> handleItineraryNotFound(NoSuchElementException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(ApiResponse.failure(new ApiError("ITINERARY_NOT_FOUND", "일정을 찾을 수 없습니다."), requestId()));
	}

	/**
	 * 🔴 S15P21E201-224 — 없는 일정이거나, 있어도 요청자가 그 일정이 속한 여행의 회원이
	 * 아니다. {@link ItineraryAccess#requireEditor} 가 편집 전에 이 예외를 던진다 — 위
	 * {@link #handleItineraryNotFound}({@code NoSuchElementException})는 {@code
	 * ItineraryEditService} 가 편집 도중에 같은 상황을 만났을 때 던지는 것이고, 이 핸들러는
	 * 편집이 시작되기 <b>전</b> 접근 판정 단계를 담당한다. 응답은 같다 — 존재를 감춘다.
	 */
	@ExceptionHandler(ItineraryQueryController.ItineraryNotFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handleAccessNotFound(
			ItineraryQueryController.ItineraryNotFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(ApiResponse.failure(new ApiError("ITINERARY_NOT_FOUND", "일정을 찾을 수 없습니다."), requestId()));
	}

	/**
	 * 🔴 S15P21E201-224 — 회원이지만 VIEWER 라 편집 권한이 없다. {@code fields} 에
	 * {@code "role=VIEWER"} 를 싣는다 — 형식은 위 클래스 javadoc 이 설명하는 계약을 따른다.
	 * 비회원의 404({@code handleItineraryNotFound})와 <b>다른 코드</b>다 — 존재를
	 * 감출 필요가 없는 회원에게 굳이 404 를 주면 화면이 "방금 보던 일정이 사라졌다" 로
	 * 잘못 해석한다.
	 */
	@ExceptionHandler(ItineraryAccess.ItineraryForbiddenException.class)
	public ResponseEntity<ApiResponse<Void>> handleForbidden(ItineraryAccess.ItineraryForbiddenException e) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
				.body(ApiResponse.failure(
						new ApiError("ITINERARY_FORBIDDEN", e.getMessage(), List.of("role=" + e.role())),
						requestId()));
	}

	/** ITN-04 에 바탕 판이 안 왔다 — 400. */
	@ExceptionHandler(ItineraryEditController.MissingBaseVersionException.class)
	public ResponseEntity<ApiResponse<Void>> handleMissingBaseVersion(
			ItineraryEditController.MissingBaseVersionException e) {
		return ResponseEntity.badRequest()
				.body(ApiResponse.failure(
						new ApiError("ITINERARY_BASE_VERSION_REQUIRED", e.getMessage(), List.of("baseVersion")),
						requestId()));
	}

	/**
	 * S15P21E201-284 — 되돌릴 편집이 없다. 오류로 죽지 않고 그 사실을 돌려준다(티켓 요구). 422 다 —
	 * 요청 모양은 맞는데 이 일정의 상태로는 할 수 없는 일이라서다. 409 는 "판이 낡았다" 에 쓰고 있어
	 * 섞지 않는다.
	 */
	@ExceptionHandler(ItineraryEditService.NothingToRevertException.class)
	public ResponseEntity<ApiResponse<Void>> handleNothingToRevert(ItineraryEditService.NothingToRevertException e) {
		return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
				.body(ApiResponse.failure(
						new ApiError("ITINERARY_NOTHING_TO_REVERT", e.getMessage(),
								List.of("latestVersion=" + e.latestVersion())),
						requestId()));
	}

	@ExceptionHandler(ItineraryEditService.RevertTargetException.class)
	public ResponseEntity<ApiResponse<Void>> handleRevertTarget(ItineraryEditService.RevertTargetException e) {
		return ResponseEntity.badRequest()
				.body(ApiResponse.failure(
						new ApiError("ITINERARY_REVERT_TARGET_INVALID", e.getMessage(),
								List.of("toVersion=" + e.toVersion(), "latestVersion=" + e.latestVersion())),
						requestId()));
	}

	private String requestId() {
		return "req_" + UUID.randomUUID();
	}
}
