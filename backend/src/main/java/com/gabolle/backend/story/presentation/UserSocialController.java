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
import com.gabolle.backend.story.application.BlockService;
import com.gabolle.backend.story.application.FollowService;
import com.gabolle.backend.story.application.StoryFeedService;
import com.gabolle.backend.story.presentation.dto.BlockResponse;
import com.gabolle.backend.story.presentation.dto.FollowResponse;
import com.gabolle.backend.story.presentation.dto.RelationListResponse;
import com.gabolle.backend.story.presentation.dto.StoryFeedResponse;
import com.gabolle.backend.story.presentation.dto.UserProfileResponse;

/**
 * 사람 단위 — 팔로우·차단·프로필·그 사람의 기록.
 *
 * <p>차단의 방향: A 가 B 를 차단하면 B 가 A 를 못 본다. A 는 (B 가 A 를 차단하지 않았다면) B 를 계속 본다.
 *
 * <p>팔로우·차단이 {@code PUT}/{@code DELETE} 인 것은 「이 상태로 만들어 달라」라서다 — 두 번 보내도 결과가 같다.
 */
@RestController
@RequestMapping("/api/v1/users/{userId}")
@Profile({ "db", "dev" })
public class UserSocialController {

	private final FollowService followService;

	private final BlockService blockService;

	private final StoryFeedService feedService;

	public UserSocialController(FollowService followService, BlockService blockService, StoryFeedService feedService) {
		this.followService = followService;
		this.blockService = blockService;
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

	/** 차단한다. 팔로우가 양쪽 다 끊기고 되돌아오지 않는다. */
	@PutMapping("/block")
	public ApiResponse<BlockResponse> block(@PathVariable UUID userId, Authentication authentication) {
		UUID me = AuthenticatedUsers.requireId(authentication);
		return ApiResponse.success(this.blockService.block(me, userId), requestId());
	}

	/** 차단을 푼다. 팔로우는 복구하지 않는다. */
	@DeleteMapping("/block")
	public ApiResponse<BlockResponse> unblock(@PathVariable UUID userId, Authentication authentication) {
		UUID me = AuthenticatedUsers.requireId(authentication);
		return ApiResponse.success(this.blockService.unblock(me, userId), requestId());
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

	/** 팔로워 목록. 그 사람이 나를 차단했으면 403 이다. */
	@GetMapping("/followers")
	public ApiResponse<RelationListResponse> followers(@PathVariable UUID userId,
			@RequestParam(value = "cursor", required = false) String cursor,
			@RequestParam(value = "limit", required = false) Integer limit,
			Authentication authentication) {
		UUID viewer = AuthenticatedUsers.requireId(authentication);
		return ApiResponse.success(this.followService.followers(viewer, userId, cursor, limit), requestId());
	}

	@GetMapping("/following")
	public ApiResponse<RelationListResponse> following(@PathVariable UUID userId,
			@RequestParam(value = "cursor", required = false) String cursor,
			@RequestParam(value = "limit", required = false) Integer limit,
			Authentication authentication) {
		UUID viewer = AuthenticatedUsers.requireId(authentication);
		return ApiResponse.success(this.followService.following(viewer, userId, cursor, limit), requestId());
	}

	/** 내가 차단한 사람 목록. {@code userId} 는 반드시 본인이어야 한다. */
	@GetMapping("/blocks")
	public ApiResponse<RelationListResponse> blocks(@PathVariable UUID userId,
			@RequestParam(value = "cursor", required = false) String cursor,
			@RequestParam(value = "limit", required = false) Integer limit,
			Authentication authentication) {
		UUID viewer = AuthenticatedUsers.requireId(authentication);
		return ApiResponse.success(this.blockService.myBlocks(viewer, userId, cursor, limit), requestId());
	}

	private static String requestId() {
		return "req_" + UUID.randomUUID();
	}
}
