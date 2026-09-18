package com.gabolle.backend.story.presentation;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.gabolle.backend.common.api.ApiError;
import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.story.application.StoryService;
import com.gabolle.backend.story.domain.UserBlock;

/**
 * {@code StorySaveController}만의 오류 응답 — {@code StoryExceptionHandler}와 같은 두 응답을
 * 낸다(STORY_NOT_FOUND · BLOCKED_BY_USER).
 *
 * <p>🔴 <b>공용 {@code StoryExceptionHandler}에 얹지 않고 따로 둔다.</b> 그 파일은 이 작업을
 * 시작한 시점에 다른 사람이 반응(좋아요/싫어요) 카운트 작업으로 잡고 있었다 — 같은 파일을
 * 두 사람이 동시에 고치면 충돌이 난다. 두 파일 다 {@code StoryService.StoryNotFoundException}과
 * {@code UserBlock.BlockedByUserException}을 같은 모양으로 번역하므로 응답은 갈리지 않는다.
 */
@RestControllerAdvice(assignableTypes = StorySaveController.class)
public class StorySaveExceptionHandler {

	@ExceptionHandler(StoryService.StoryNotFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handleNotFound(StoryService.StoryNotFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(ApiResponse.failure(new ApiError("STORY_NOT_FOUND", e.getMessage(), List.of()), requestId()));
	}

	@ExceptionHandler(UserBlock.BlockedByUserException.class)
	public ResponseEntity<ApiResponse<Void>> handleBlockedByUser(UserBlock.BlockedByUserException e) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
				.body(ApiResponse.failure(new ApiError("BLOCKED_BY_USER", e.getMessage(), List.of()), requestId()));
	}

	private static String requestId() {
		return "req_" + UUID.randomUUID();
	}
}
