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

	/**
	 * S15P21E201-467 — 여행 기간을 벗어난 날에 장소를 더하려 했다 — 400.
	 *
	 * <p>🔴 표의 CHECK 가 막지 못하는 종류다. {@code itinerary_item.visit_date} 가 여행 기간
	 * 밖이어도 그 제약은 통과한다 — 표를 건너는 검사라서(마이그레이션 {@code V20260905120000}
	 * 주석 "막지 못하는 것"). 여기서 막지 않으면 어느 날 화면에도 안 나타나는 항목이 저장된다.
	 */
	/**
	 * S15P21E201-91 — 보낸 순서가 그날의 항목 전부와 일치하지 않는다 — 400.
	 *
	 * <p>🔴 무엇이 어긋났는지를 함께 싣는다. 화면이 <b>자기 목록이 낡은 것인지</b>(다시 조회하면
	 * 된다) <b>항목을 빠뜨린 것인지</b>(보내는 쪽 버그다)를 구분할 수 있어야 한다.
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
	 * S15P21E201-91 — 고정된 방문지의 자리가 바뀌려 한다 — 409.
	 *
	 * <p>409 로 두는 이유는 {@code ITINERARY_VERSION_CONFLICT} 와 같다. <b>요청은 맞는데 지금
	 * 상태와 부딪힌다.</b> 화면은 그 항목의 고정을 먼저 풀고 다시 보내면 된다 — 그래서 어느
	 * 항목이 막았는지를 응답에 싣는다.
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
	 * S15P21E201-467 — 기간이 정해진 장소가 여행 기간 내내 열리지 않는다 — 422.
	 *
	 * <p>422 로 두는 이유는 {@code ITINERARY_NOTHING_TO_REVERT} 와 같다. 요청 모양은 맞는데 이
	 * 여행의 날짜로는 할 수 없는 일이다. 아래 {@code ITINERARY_PLACE_CLOSED_ON_DAY} 와 <b>다른
	 * 코드·다른 상태 코드</b>인 것이 핵심이다 — 이쪽은 날짜를 바꿔 다시 보내도 안 되므로 화면이
	 * 날짜 선택기를 띄우면 안 되고 "이 여행 기간에는 열리지 않습니다" 로 끝내야 한다.
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
	 * 🔴 S15P21E201-467 — 그 날에는 안 열리지만 <b>다른 날에는 열린다</b> — 400. 고칠 수 있는
	 * 요청이므로 <b>넣을 수 있는 날들을 함께 싣는다.</b>
	 *
	 * <p>{@code availableDayIndexes} 는 며칠째인지(화면의 탭), {@code availableDates} 는 실제
	 * 날짜(사람이 읽을 문장)다. 둘의 순서가 서로 짝이 맞는다 — 같은 목록을 두 모양으로 적은
	 * 것이다. {@code fields} 문자열 형식은 이 클래스 javadoc 이 설명하는 계약을 따르고, 값이 여러
	 * 개인 칸은 쉼표로 잇는다.
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
	 * S15P21E201-308 — 다시 짠 시간표가 그날 안에 안 들어간다 — 422.
	 *
	 * <p>400 이 아닌 이유는 요청 자체는 멀쩡하기 때문이다. 형식도 맞고 권한도 있는데 지금
	 * 상태에서는 그 요청을 들어줄 수 없는 것이라, 문법 오류를 뜻하는 400 보다 "무슨 말인지는
	 * 알겠으나 처리할 수 없다" 는 422 가 맞다. 영업시간 밖 장소를 넣으려 할 때와 같은 자리다.
	 *
	 * <p>넘치는 방문지를 함께 실어 보낸다. 화면이 "이 셋을 빼면 들어갑니다" 라고 말할 수
	 * 있어야 사용자가 다음 행동을 정한다 — 그냥 거절만 하면 사용자는 무엇을 고쳐야 할지 모른다.
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
	 * S15P21E201-467 — 이미 담긴 장소를 또 담으려 했다 — 409. 조용히 성공시키지 않는 이유는
	 * {@code ItineraryEditService.requireAddable} javadoc 에 있다.
	 *
	 * <p>{@code ITINERARY_VERSION_CONFLICT} 와 상태 코드는 같고 코드는 다르다. 앞의 것은 "최신
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
