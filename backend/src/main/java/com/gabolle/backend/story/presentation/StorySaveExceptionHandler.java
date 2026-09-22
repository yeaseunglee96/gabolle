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
 * {@code StorySaveController} 만의 오류 응답. {@code StoryExceptionHandler} 와 같은 두 응답
 * (STORY_NOT_FOUND · BLOCKED_BY_USER)을 같은 모양으로 낸다 — 한쪽만 고치면 응답이 갈린다.
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
