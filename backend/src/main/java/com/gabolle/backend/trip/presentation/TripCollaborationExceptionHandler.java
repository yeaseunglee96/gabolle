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
import com.gabolle.backend.trip.application.TripInviteService;
import com.gabolle.backend.trip.application.TripMemberService;
import com.gabolle.backend.trip.application.TripQueryService;

/**
 * 동행자 초대·참여자 관리의 오류를 HTTP 로 번역한다 — S15P21E201-294 · -299 · -320.
 *
 * <p>🔴 {@link TripExceptionHandler} 를 넓히지 않는다. 그 처리기는 여행 생성·조회 전용이고
 * {@code assignableTypes = TripController.class} 로 범위가 좁혀져 있어 이 컨트롤러의 예외는
 * 어차피 거기서 안 잡힌다 — 그래서 이 컨트롤러 전용 처리기를 새로 둔다.
 */
// 갈래 열람 기록 경로(S15P21E201-475)도 이 번역기를 쓴다. 그 경로가 여행 회원 판정에
// 같은 예외(TripQueryService.TripNotFoundException)를 쓰므로, 번역을 새로 만들면 같은
// 잘못에 다른 응답이 나간다.
@RestControllerAdvice(assignableTypes = { TripCollaborationController.class, TripFacetViewController.class })
public class TripCollaborationExceptionHandler {

	/**
	 * 🔴 티켓 완료 기준 — "어느 항목이 빠졌는지" 가 응답에 들어 있다. {@code TripExceptionHandler}
	 * 와 같은 번역이다.
	 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
		List<String> fields = e.getBindingResult().getFieldErrors().stream()
				.map(f -> f.getField() + ": " + f.getDefaultMessage())
				.toList();
		return ResponseEntity.badRequest().body(ApiResponse.failure(
				new ApiError("TRIP_VALIDATION_FAILED", "error.trip.validation", fields), requestId()));
	}

	/** 없는 여행이거나, 요청자가 그 여행의 회원이 아니다. 둘을 구분해 응답하지 않는다. */
	@ExceptionHandler(TripQueryService.TripNotFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handleTripNotFound(TripQueryService.TripNotFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.failure(
				new ApiError("TRIP_NOT_FOUND", "error.trip.notFound"), requestId()));
	}

	/** 회원이지만 그 여행의 소유자가 아니라서 초대를 발급할 수 없다. */
	@ExceptionHandler(TripInviteService.TripInviteForbiddenException.class)
	public ResponseEntity<ApiResponse<Void>> handleInviteForbidden(TripInviteService.TripInviteForbiddenException e) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.failure(
				new ApiError("TRIP_FORBIDDEN", e.getMessage()), requestId()));
	}

	/** 회원이지만 그 여행의 소유자가 아니라서 참여자를 바꾸거나 뺄 수 없다. */
	@ExceptionHandler(TripMemberService.TripMemberForbiddenException.class)
	public ResponseEntity<ApiResponse<Void>> handleMemberForbidden(TripMemberService.TripMemberForbiddenException e) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.failure(
				new ApiError("TRIP_FORBIDDEN", e.getMessage()), requestId()));
	}

	/** {@code role} 이 {@code EDITOR}·{@code VIEWER} 가 아니다({@code OWNER} 포함). */
	@ExceptionHandler(TripInviteService.InvalidInviteRoleException.class)
	public ResponseEntity<ApiResponse<Void>> handleInvalidInviteRole(TripInviteService.InvalidInviteRoleException e) {
		return ResponseEntity.badRequest().body(ApiResponse.failure(
				new ApiError("TRIP_INVITE_ROLE_INVALID", e.getMessage()), requestId()));
	}

	/** 표(token)가 존재하지 않는다. */
	@ExceptionHandler(TripInviteService.TripInviteNotFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handleInviteNotFound(TripInviteService.TripInviteNotFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.failure(
				new ApiError("TRIP_INVITE_NOT_FOUND", e.getMessage()), requestId()));
	}

	/** 표는 있지만 발급 후 7일이 지났다 — 행은 그대로 둔다. */
	@ExceptionHandler(TripInviteService.TripInviteExpiredException.class)
	public ResponseEntity<ApiResponse<Void>> handleInviteExpired(TripInviteService.TripInviteExpiredException e) {
		return ResponseEntity.status(HttpStatus.GONE).body(ApiResponse.failure(
				new ApiError("TRIP_INVITE_EXPIRED", e.getMessage()), requestId()));
	}

	/** 역할 변경·제거의 대상이 그 여행의 회원이 아니다. */
	@ExceptionHandler(TripMemberService.TripMemberNotFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handleMemberNotFound(TripMemberService.TripMemberNotFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.failure(
				new ApiError("TRIP_MEMBER_NOT_FOUND", e.getMessage()), requestId()));
	}

	/**
	 * 🔴 {@code role} 문자열 파싱 실패와 {@code TripMember.withRole}(대상이 OWNER 이거나 새
	 * 역할이 OWNER)이 던지는 것 둘 다 여기로 온다 — 둘 다 "역할 값이 유효하지 않다" 는 같은
	 * 종류의 오류라 코드를 하나로 합친다.
	 */
	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException e) {
		return ResponseEntity.badRequest().body(ApiResponse.failure(
				new ApiError("TRIP_MEMBER_ROLE_INVALID", e.getMessage()), requestId()));
	}

	private String requestId() {
		return "req_" + UUID.randomUUID();
	}
}
