package com.gabolle.backend.itinerary.presentation;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;

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
 * 팀 공용 봉투를 쓰고 구조화된 값은 {@code fields} 에 {@code "이름=값"} 문자열로 넣는다.
 * 앱이 {@code error.fields} 에서 {@code /^latestVersion=/} 로 최신 판 번호를 뽑으므로 그 형식
 * 자체가 계약이다 — 이름을 바꾸면 앱이 못 읽고, 그 사실을 회귀 테스트가 지킨다.
 * {@code assignableTypes} 로 이 컨트롤러 하나만 본다. 범위 없는 advice 가 남의 예외를 가로챈
 * 사고가 이미 있었다. {@code @Order} 도 같은 이유다 — 순서를 안 주면 승자가 컴포넌트 스캔
 * 순서로 정해진다.
 * {@code message} 는 한국어 문장이다. 앱이 {@code error.message} 를 그대로 화면에 띄우는
 * 자리가 있다.
 */
@RestControllerAdvice(assignableTypes = ItineraryEditController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ItineraryExceptionHandler {

	public static final String CONFLICT_CODE = "ITINERARY_VERSION_CONFLICT";

	@ExceptionHandler(StaleItineraryVersionException.class)
	public ResponseEntity<ApiResponse<Void>> handleStaleVersion(StaleItineraryVersionException e) {
		// 이 두 문자열의 모양이 앱과의 계약이다. 앱은 latestVersion= 뒤의 숫자를 정규식으로 읽는다.
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
	 * 없는 일정이거나, 있어도 요청자가 그 일정이 속한 여행의 회원이 아니다.
	 * {@link ItineraryAccess#requireEditor} 가 편집 전에 이 예외를 던진다 —
	 * {@link #handleItineraryNotFound} 는 편집 도중에 같은 상황을 만났을 때 오는 것이고, 이
	 * 핸들러는 편집이 시작되기 전 접근 판정 단계를 담당한다. 응답은 같다 — 존재를 감춘다.
	 */
	@ExceptionHandler(ItineraryQueryController.ItineraryNotFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handleAccessNotFound(
			ItineraryQueryController.ItineraryNotFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(ApiResponse.failure(new ApiError("ITINERARY_NOT_FOUND", "일정을 찾을 수 없습니다."), requestId()));
	}

	/**
	 * 회원이지만 VIEWER 라 편집 권한이 없다. {@code fields} 에 {@code "role=VIEWER"} 를 싣는다.
	 * 비회원의 404 와 다른 코드다 — 존재를 감출 필요가 없는 회원에게 굳이 404 를 주면 화면이
	 * "방금 보던 일정이 사라졌다" 로 잘못 해석한다.
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
	 * 되돌릴 편집이 없다. 오류로 죽지 않고 그 사실을 돌려준다. 422 인 이유는 요청 모양은 맞는데
	 * 이 일정의 상태로는 할 수 없는 일이라서다 — 409 는 "판이 낡았다" 에 쓰고 있어 섞지 않는다.
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

	/**
	 * 여행 기간을 벗어난 날에 장소를 더하려 했다 — 400.
	 * 표의 CHECK 가 막지 못하는 종류다. {@code itinerary_item.visit_date} 가 여행 기간 밖이어도
	 * 그 제약은 통과한다(표를 건너는 검사라서). 여기서 막지 않으면 어느 날 화면에도 안 나타나는
	 * 항목이 저장된다.
	 */
	/**
	 * 보낸 순서가 그날의 항목 전부와 일치하지 않는다 — 400.
	 * 무엇이 어긋났는지를 함께 싣는다. 화면이 자기 목록이 낡은 것인지(다시 조회하면 된다)
	 * 항목을 빠뜨린 것인지(보내는 쪽 버그다)를 구분할 수 있어야 한다.
	 */
	@ExceptionHandler(ItineraryRevision.DayOrderMismatchException.class)
	public ResponseEntity<ApiResponse<Void>> handleDayOrderMismatch(
			ItineraryRevision.DayOrderMismatchException e) {
		return ResponseEntity.badRequest()
				.body(ApiResponse.failure(
						new ApiError("ITINERARY_DAY_ORDER_MISMATCH", e.getMessage(),
								List.of("dayIndex=" + e.dayIndex())),
						requestId()));
	}

	/**
	 * 고정된 방문지의 자리가 바뀌려 한다 — 409.
	 * 409 인 이유는 {@code ITINERARY_VERSION_CONFLICT} 와 같다. 요청은 맞는데 지금 상태와
	 * 부딪힌다. 화면은 그 항목의 고정을 먼저 풀고 다시 보내면 되므로 어느 항목이 막았는지를 싣는다.
	 */
	@ExceptionHandler(ItineraryRevision.LockedItemMovedException.class)
	public ResponseEntity<ApiResponse<Void>> handleLockedItemMoved(
			ItineraryRevision.LockedItemMovedException e) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(ApiResponse.failure(
						new ApiError("ITINERARY_LOCKED_ITEM_MOVED", e.getMessage(),
								List.of("itemKey=" + e.itemKey(), "dayIndex=" + e.dayIndex())),
						requestId()));
	}

	@ExceptionHandler(ItineraryEditController.DayOutsideTripException.class)
	public ResponseEntity<ApiResponse<Void>> handleDayOutsideTrip(
			ItineraryEditController.DayOutsideTripException e) {
		return ResponseEntity.badRequest()
				.body(ApiResponse.failure(
						new ApiError("ITINERARY_DAY_OUTSIDE_TRIP", e.getMessage(),
								List.of("dayIndex=" + e.dayIndex())),
						requestId()));
	}

	/**
	 * 기간이 정해진 장소가 여행 기간 내내 열리지 않는다 — 422.
	 * 아래 {@code ITINERARY_PLACE_CLOSED_ON_DAY} 와 다른 코드·다른 상태 코드인 것이 핵심이다.
	 * 이쪽은 날짜를 바꿔 다시 보내도 안 되므로 화면이 날짜 선택기를 띄우면 안 되고 "이 여행
	 * 기간에는 열리지 않습니다" 로 끝내야 한다.
	 */
	@ExceptionHandler(ItineraryEditService.PlaceNotOpenDuringTripException.class)
	public ResponseEntity<ApiResponse<Void>> handlePlaceNotOpenDuringTrip(
			ItineraryEditService.PlaceNotOpenDuringTripException e) {
		return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
				.body(ApiResponse.failure(
						new ApiError("ITINERARY_PLACE_NOT_OPEN_DURING_TRIP", e.getMessage(),
								List.of("tripStartDate=" + e.tripStartDate(),
										"tripFinishDate=" + e.tripFinishDate())),
						requestId()));
	}

	/**
	 * 그 날에는 안 열리지만 다른 날에는 열린다 — 400. 고칠 수 있는 요청이므로 넣을 수 있는
	 * 날들을 함께 싣는다.
	 * {@code availableDayIndexes} 는 며칠째인지(화면의 탭), {@code availableDates} 는 실제
	 * 날짜(사람이 읽을 문장)다. 둘의 순서가 서로 짝이 맞는다. 값이 여러 개인 칸은 쉼표로 잇는다.
	 */
	@ExceptionHandler(ItineraryEditService.PlaceClosedOnDayException.class)
	public ResponseEntity<ApiResponse<Void>> handlePlaceClosedOnDay(
			ItineraryEditService.PlaceClosedOnDayException e) {
		List<String> fields = List.of(
				"requestedDayIndex=" + e.dayIndex(),
				"requestedDate=" + e.requestedDate(),
				"availableDayIndexes=" + join(e.openDayIndexes()),
				"availableDates=" + join(e.openDates()));

		return ResponseEntity.badRequest()
				.body(ApiResponse.failure(
						new ApiError("ITINERARY_PLACE_CLOSED_ON_DAY", e.getMessage(), fields),
						requestId()));
	}

	/**
	 * 다시 짠 시간표가 그날 안에 안 들어간다 — 422.
	 * 400 이 아닌 이유는 요청 자체는 멀쩡하기 때문이다. 형식도 맞고 권한도 있는데 지금 상태에서
	 * 들어줄 수 없는 것이라, 문법 오류를 뜻하는 400 보다 422 가 맞다.
	 * 넘치는 방문지를 함께 실어 보낸다. 화면이 "이 셋을 빼면 들어갑니다" 라고 말할 수 있어야
	 * 사용자가 다음 행동을 정한다.
	 */
	@ExceptionHandler(ItineraryEditService.ReplanOverflowsDayException.class)
	public ResponseEntity<ApiResponse<Void>> handleReplanOverflowsDay(
			ItineraryEditService.ReplanOverflowsDayException e) {
		List<String> fields = List.of(
				"dayIndex=" + e.dayIndex(),
				"overflowingItemIds=" + join(e.overflowingItemKeys()));

		return ResponseEntity.unprocessableEntity()
				.body(ApiResponse.failure(
						new ApiError("ITINERARY_REPLAN_OVERFLOWS_DAY", e.getMessage(), fields),
						requestId()));
	}

	/**
	 * 이미 담긴 장소를 또 담으려 했다 — 409. 조용히 성공시키지 않는 이유는
	 * {@code ItineraryEditService.requireAddable} javadoc 에 있다.
	 * {@code ITINERARY_VERSION_CONFLICT} 와 상태 코드는 같고 코드는 다르다. 앞의 것은 "최신
	 * 일정을 다시 불러와라" 이고 이것은 "이미 담겨 있다" 라서 화면이 할 일이 정반대다.
	 */
	@ExceptionHandler(ItineraryEditService.PlaceAlreadyInItineraryException.class)
	public ResponseEntity<ApiResponse<Void>> handlePlaceAlreadyAdded(
			ItineraryEditService.PlaceAlreadyInItineraryException e) {
		List<String> fields = List.of(
				"existingDayIndex=" + e.existingDayIndex(),
				"existingDayIndexes=" + join(e.existingDayIndexes()),
				"requestedDayIndex=" + e.requestedDayIndex());

		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(ApiResponse.failure(
						new ApiError("ITINERARY_PLACE_ALREADY_ADDED", e.getMessage(), fields),
						requestId()));
	}

	/** 여러 값을 한 {@code fields} 칸에 담는 형식 — 쉼표로 잇는다. 빈 목록은 빈 문자열이다. */
	private static String join(List<?> values) {
		return values.stream().map(String::valueOf).collect(Collectors.joining(","));
	}

	/** {@code dayIndex} 가 음수다 — 400. */
	@ExceptionHandler(ItineraryRevision.DayIndexOutOfRangeException.class)
	public ResponseEntity<ApiResponse<Void>> handleDayIndexOutOfRange(
			ItineraryRevision.DayIndexOutOfRangeException e) {
		return ResponseEntity.badRequest()
				.body(ApiResponse.failure(
						new ApiError("INVALID_REQUEST", e.getMessage(),
								List.of("dayIndex=" + e.dayIndex())),
						requestId()));
	}

	private String requestId() {
		return "req_" + UUID.randomUUID();
	}
}
