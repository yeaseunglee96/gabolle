package com.gabolle.backend.story.presentation;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.common.security.AuthenticatedUsers;
import com.gabolle.backend.story.application.StoryCoauthorService;
import com.gabolle.backend.story.presentation.dto.AcceptStoryInviteResponse;
import com.gabolle.backend.story.presentation.dto.AddStoryCoauthorsRequest;
import com.gabolle.backend.story.presentation.dto.StoryCoauthorsResponse;
import com.gabolle.backend.story.presentation.dto.StoryInviteResponse;

import jakarta.validation.Valid;

/** 기록 공동 작성 — 초대 발급·수락·참여자 관리. 오류 번역은 {@link StoryExceptionHandler} 가 함께 맡는다. */
@RestController
@Profile({ "db", "dev" })
public class StoryCoauthorController {

	private final StoryCoauthorService coauthorService;

	public StoryCoauthorController(StoryCoauthorService coauthorService) {
		this.coauthorService = coauthorService;
	}

	/** 초대 링크 발급. 만든 사람만 부를 수 있다. */
	@PostMapping("/api/v1/stories/{storyId}/invites")
	public ResponseEntity<ApiResponse<StoryInviteResponse>> createInvite(
			@PathVariable UUID storyId,
			Authentication authentication) {

		UUID requester = AuthenticatedUsers.requireId(authentication);
		StoryInviteResponse response = this.coauthorService.issueInvite(storyId, requester);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response, requestId()));
	}

	/** 초대 수락. 표(token)를 아는 로그인 사용자면 누구나 부를 수 있다 — 표 자체가 유일한 잠금이다. */
	@PostMapping("/api/v1/story-invites/{token}/accept")
	public ApiResponse<AcceptStoryInviteResponse> acceptInvite(
			@PathVariable String token,
			Authentication authentication) {

		UUID requester = AuthenticatedUsers.requireId(authentication);
		return ApiResponse.success(this.coauthorService.accept(token, requester), requestId());
	}

	/** 참여자 목록. 그 기록을 볼 수 있는 사람이면 누구나. */
	@GetMapping("/api/v1/stories/{storyId}/coauthors")
	public ApiResponse<StoryCoauthorsResponse> coauthors(
			@PathVariable UUID storyId,
			Authentication authentication) {

		UUID requester = AuthenticatedUsers.requireId(authentication);
		return ApiResponse.success(this.coauthorService.list(storyId, requester), requestId());
	}

	/** 여행 동행자를 골라 공동 작성자로 넣는다. 만든 사람만 부를 수 있다. */
	@PostMapping("/api/v1/stories/{storyId}/coauthors")
	public ResponseEntity<Void> addCoauthors(
			@PathVariable UUID storyId,
			@Valid @RequestBody AddStoryCoauthorsRequest request,
			Authentication authentication) {

		UUID requester = AuthenticatedUsers.requireId(authentication);
		this.coauthorService.addTripMembers(storyId, requester, request.userIds());
		return ResponseEntity.noContent().build();
	}

	/** 참여자 제거(또는 나가기). 만든 사람이 남을 빼거나, 공동 작성자가 자기 자신을 뺀다. */
	@DeleteMapping("/api/v1/stories/{storyId}/coauthors/{userId}")
	public ResponseEntity<Void> removeCoauthor(
			@PathVariable UUID storyId,
			@PathVariable UUID userId,
			Authentication authentication) {

		UUID requester = AuthenticatedUsers.requireId(authentication);
		this.coauthorService.remove(storyId, requester, userId);
		return ResponseEntity.noContent().build();
	}

	private String requestId() {
		return "req_" + UUID.randomUUID();
	}
}
