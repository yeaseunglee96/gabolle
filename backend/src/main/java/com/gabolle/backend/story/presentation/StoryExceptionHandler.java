package com.gabolle.backend.story.presentation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.gabolle.backend.common.api.ApiError;
import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.story.application.BlockService;
import com.gabolle.backend.story.application.FeedCursor;
import com.gabolle.backend.story.application.FollowService;
import com.gabolle.backend.story.application.RelationCursor;
import com.gabolle.backend.story.application.StoryCoauthorService;
import com.gabolle.backend.story.application.StoryFeedService;
import com.gabolle.backend.story.application.StoryReactionService;
import com.gabolle.backend.story.application.StoryService;
import com.gabolle.backend.story.domain.UserBlock;
import com.gabolle.backend.story.domain.UserFollow;

/**
 * 기록·팔로우 컨트롤러의 오류 응답.
 *
 * <p>못 보는 기록(없음·지움·나만 보기·팔로워 전용)은 전부 404 {@code STORY_NOT_FOUND} 로 같은 응답을 낸다 —
 * 응답이 갈리면 그 차이가 곧 존재 사실의 유출이다. 403 {@code STORY_FORBIDDEN} 은 보이는 기록을 남이 고치거나
 * 지우려 할 때만 낸다.
 */
@RestControllerAdvice(
		assignableTypes = { StoryController.class, UserSocialController.class, StoryCoauthorController.class,
				StoryReactionController.class, MyRepliesController.class })
@Order(Ordered.HIGHEST_PRECEDENCE)
public class StoryExceptionHandler {

	@ExceptionHandler(StoryService.StoryNotFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handleNotFound(StoryService.StoryNotFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(ApiResponse.failure(new ApiError("STORY_NOT_FOUND", e.getMessage(), List.of()), requestId()));
	}

	@ExceptionHandler(StoryService.StoryForbiddenException.class)
	public ResponseEntity<ApiResponse<Void>> handleForbidden(StoryService.StoryForbiddenException e) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
				.body(ApiResponse.failure(new ApiError("STORY_FORBIDDEN", e.getMessage(), List.of()), requestId()));
	}

	/**
	 * 검토로 감춰진 기록을 고치려 했다 — 409.
	 *
	 * <p>403 과 가르는 이유는 화면이 할 말이 다르기 때문이다. 403 은 「내 기록이 아니다」이고 이것은
	 * 「내 기록이지만 지금은 못 고친다」라, 같은 코드로 답하면 사용자가 자기 글을 남의 글로 오해한다.
	 * 지우는 것은 여전히 된다.
	 */
	@ExceptionHandler(StoryService.StoryUnderModerationException.class)
	public ResponseEntity<ApiResponse<Void>> handleUnderModeration(StoryService.StoryUnderModerationException e) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(ApiResponse.failure(new ApiError("STORY_UNDER_MODERATION", e.getMessage(), List.of()),
						requestId()));
	}

	@ExceptionHandler(StoryService.InvalidReferenceException.class)
	public ResponseEntity<ApiResponse<Void>> handleInvalidReference(StoryService.InvalidReferenceException e) {
		return ResponseEntity.badRequest()
				.body(ApiResponse.failure(new ApiError("STORY_REFERENCE_INVALID", e.getMessage(), List.of(e.field())),
						requestId()));
	}

	@ExceptionHandler(StoryCoauthorService.StoryInviteNotFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handleStoryInviteNotFound(
			StoryCoauthorService.StoryInviteNotFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(ApiResponse.failure(new ApiError("STORY_INVITE_NOT_FOUND", e.getMessage(), List.of()),
						requestId()));
	}

	/** 만료는 404 가 아니라 410 이다 — 표는 있었지만 지금은 못 쓴다는 뜻을 그대로 전한다. */
	@ExceptionHandler(StoryCoauthorService.StoryInviteExpiredException.class)
	public ResponseEntity<ApiResponse<Void>> handleStoryInviteExpired(
			StoryCoauthorService.StoryInviteExpiredException e) {
		return ResponseEntity.status(HttpStatus.GONE)
				.body(ApiResponse.failure(new ApiError("STORY_INVITE_EXPIRED", e.getMessage(), List.of()),
						requestId()));
	}

	@ExceptionHandler(StoryCoauthorService.StoryHasNoTripException.class)
	public ResponseEntity<ApiResponse<Void>> handleStoryHasNoTrip(StoryCoauthorService.StoryHasNoTripException e) {
		return ResponseEntity.badRequest()
				.body(ApiResponse.failure(new ApiError("STORY_HAS_NO_TRIP", e.getMessage(), List.of("tripId")),
						requestId()));
	}

	@ExceptionHandler(StoryCoauthorService.NotTripMemberException.class)
	public ResponseEntity<ApiResponse<Void>> handleNotTripMember(StoryCoauthorService.NotTripMemberException e) {
		return ResponseEntity.badRequest()
				.body(ApiResponse.failure(new ApiError("STORY_COAUTHOR_NOT_TRIP_MEMBER", e.getMessage(),
						List.of("userIds")), requestId()));
	}

	/**
	 * 내가 함께 쓰는 글에 반응하려 했다 — 409.
	 *
	 * <p>403 은 보이는 글을 남이 고치거나 지우려 할 때다. 이것은 반대로 내 글이라서 막힌 것이라, 같은 코드로
	 * 답하면 화면이 「남의 글이라 안 된다」를 띄운다. 글은 보이므로 404 도 아니다.
	 */
	@ExceptionHandler(StoryReactionService.OwnReactionNotAllowedException.class)
	public ResponseEntity<ApiResponse<Void>> handleOwnReaction(StoryReactionService.OwnReactionNotAllowedException e) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(ApiResponse.failure(new ApiError("STORY_REACTION_OWN", e.getMessage(), List.of()), requestId()));
	}

	/**
	 * 로그인하지 않은 사람의 팔로잉 피드. 401 이 아니라 400 인 이유는
	 * {@code StoryFeedService.AnonymousFollowingFeedException} 에 적어 뒀다 — 앱이 401 을 출입증 만료로
	 * 보고 다시 발급받아 재시도한다.
	 */
	@ExceptionHandler(StoryFeedService.AnonymousFollowingFeedException.class)
	public ResponseEntity<ApiResponse<Void>> handleAnonymousFollowing(
			StoryFeedService.AnonymousFollowingFeedException e) {
		return ResponseEntity.badRequest()
				.body(ApiResponse.failure(new ApiError("STORY_FEED_LOGIN_REQUIRED", e.getMessage(), List.of("scope")),
						requestId()));
	}

	@ExceptionHandler(FeedCursor.InvalidCursorException.class)
	public ResponseEntity<ApiResponse<Void>> handleCursor(FeedCursor.InvalidCursorException e) {
		return ResponseEntity.badRequest()
				.body(ApiResponse.failure(new ApiError("FEED_CURSOR_INVALID", e.getMessage(), List.of("cursor")),
						requestId()));
	}

	@ExceptionHandler(UserFollow.SelfFollowException.class)
	public ResponseEntity<ApiResponse<Void>> handleSelfFollow(UserFollow.SelfFollowException e) {
		return ResponseEntity.badRequest()
				.body(ApiResponse.failure(new ApiError("FOLLOW_SELF", e.getMessage(), List.of("userId")), requestId()));
	}

	@ExceptionHandler(UserBlock.SelfBlockException.class)
	public ResponseEntity<ApiResponse<Void>> handleSelfBlock(UserBlock.SelfBlockException e) {
		return ResponseEntity.badRequest()
				.body(ApiResponse.failure(new ApiError("BLOCK_SELF", e.getMessage(), List.of("userId")), requestId()));
	}

	/**
	 * 나를 차단한 사람의 프로필·기록을 열었다 — 404 가 아니라 403 이다.
	 *
	 * <p>화면이 「차단되어 볼 수 없습니다」를 띄우려면 「없다」와 「막혔다」를 가를 수 있어야 한다. 대신 차단
	 * 사실이 상대에게 드러난다 — 바꾸려면 이 한 곳과 화면 문구를 고친다.
	 */
	@ExceptionHandler(UserBlock.BlockedByUserException.class)
	public ResponseEntity<ApiResponse<Void>> handleBlockedByUser(UserBlock.BlockedByUserException e) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
				.body(ApiResponse.failure(new ApiError("BLOCKED_BY_USER", e.getMessage(), List.of()), requestId()));
	}

	@ExceptionHandler(RelationCursor.InvalidCursorException.class)
	public ResponseEntity<ApiResponse<Void>> handleRelationCursor(RelationCursor.InvalidCursorException e) {
		return ResponseEntity.badRequest()
				.body(ApiResponse.failure(new ApiError("FEED_CURSOR_INVALID", e.getMessage(), List.of("cursor")),
						requestId()));
	}

	@ExceptionHandler(BlockService.BlockListForbiddenException.class)
	public ResponseEntity<ApiResponse<Void>> handleBlockListForbidden(BlockService.BlockListForbiddenException e) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
				.body(ApiResponse.failure(new ApiError("BLOCK_LIST_FORBIDDEN", e.getMessage(), List.of()), requestId()));
	}

	@ExceptionHandler(FollowService.UserNotFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handleUserNotFound(FollowService.UserNotFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(ApiResponse.failure(new ApiError("USER_NOT_FOUND", e.getMessage(), List.of()), requestId()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
		List<String> fields = new ArrayList<>();
		for (FieldError error : e.getBindingResult().getFieldErrors()) {
			fields.add(error.getField());
		}
		return ResponseEntity.badRequest()
				.body(ApiResponse.failure(new ApiError("STORY_VALIDATION_FAILED", "요청 값을 확인해 주세요.", fields),
						requestId()));
	}

	/**
	 * {@code IllegalArgumentException} 을 여기 넣지 않는다. 스프링은 예외의 원인 사슬까지 훑어 핸들러를 고르므로,
	 * 저장소 안쪽에서 난 IAE(예: DB 값이 enum 에 없다)가 400 으로 둔갑해 진짜 서버 결함이 「요청이 틀렸다」로 보인다.
	 */
	@ExceptionHandler({ HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class })
	public ResponseEntity<ApiResponse<Void>> handleBadInput(Exception e) {
		return ResponseEntity.badRequest()
				.body(ApiResponse.failure(new ApiError("STORY_VALIDATION_FAILED", "요청 값을 읽을 수 없습니다.", List.of()),
						requestId()));
	}

	private static String requestId() {
		return "req_" + UUID.randomUUID();
	}
}
