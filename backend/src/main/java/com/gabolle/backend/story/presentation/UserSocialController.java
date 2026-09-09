package com.gabolle.backend.story.presentation;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.common.security.AuthenticatedUsers;
import com.gabolle.backend.story.application.FollowService;
import com.gabolle.backend.story.application.StoryFeedService;
import com.gabolle.backend.story.presentation.dto.FollowResponse;
import com.gabolle.backend.story.presentation.dto.StoryFeedResponse;
import com.gabolle.backend.story.presentation.dto.UserProfileResponse;

/**
 * 사람 단위 — 팔로우·해제·프로필·그 사람의 기록. S15P21E201-242 · -126.
 *
 * <pre>
 * PUT    /api/v1/users/{userId}/follow                 팔로우 (멱등, 200)
 * DELETE /api/v1/users/{userId}/follow                 해제 (멱등, 200)
 * GET    /api/v1/users/{userId}/profile                프로필 머리
 * GET    /api/v1/users/{userId}/stories?cursor=&limit= 그 사람의 기록(보이는 범위만)
 * </pre>
 *
 * <p>{@code PUT} 인 이유 — 팔로우는 "이 상태로 만들어 달라" 이지 "하나 더 만들어 달라" 가 아니다. 두 번 보내도
 * 결과가 같아야 하고 HTTP 에서 그 뜻의 동사가 PUT 이다.
 */
@RestController
@RequestMapping("/api/v1/users/{userId}")
@Profile({ "db", "dev" })
public class UserSocialController {

	private final FollowService followService;

	private final StoryFeedService feedService;

	public UserSocialController(FollowService followService, StoryFeedService feedService) {
		this.followService = followService;
		this.feedService = feedService;
	}

	@PutMapping("/follow")
	public ApiResponse<FollowResponse> follow(@PathVariable UUID userId, Authentication authentication) {
		UUID me = AuthenticatedUsers.requireId(authentication);
		return ApiResponse.success(this.followService.follow(me, userId), requestId());
	}

	@DeleteMapping("/follow")
	public ApiResponse<FollowResponse> unfollow(@PathVariable UUID userId, Authentication authentication) {
		UUID me = AuthenticatedUsers.requireId(authentication);
		return ApiResponse.success(this.followService.unfollow(me, userId), requestId());
	}

	@GetMapping("/profile")
	public ApiResponse<UserProfileResponse> profile(@PathVariable UUID userId, Authentication authentication) {
		UUID viewer = AuthenticatedUsers.requireId(authentication);
		return ApiResponse.success(this.followService.profile(viewer, userId), requestId());
	}

	@GetMapping("/stories")
	public ApiResponse<StoryFeedResponse> stories(@PathVariable UUID userId,
			@RequestParam(value = "cursor", required = false) String cursor,
			@RequestParam(value = "limit", required = false) Integer limit,
			Authentication authentication) {
		UUID viewer = AuthenticatedUsers.requireId(authentication);
		return ApiResponse.success(this.feedService.authorFeed(viewer, userId, cursor, limit), requestId());
	}

	private static String requestId() {
		return "req_" + UUID.randomUUID();
	}
}
