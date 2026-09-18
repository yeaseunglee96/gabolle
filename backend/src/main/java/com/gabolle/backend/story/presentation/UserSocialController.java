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
 * 사람 단위 — 팔로우·차단·프로필·그 사람의 기록. S15P21E201-242 · -126 · -990.
 *
 * <pre>
 * PUT    /api/v1/users/{userId}/follow                   팔로우 (멱등, 200)
 * DELETE /api/v1/users/{userId}/follow                   해제 (멱등, 200)
 * PUT    /api/v1/users/{userId}/block                    차단 (멱등, 200) — 팔로우가 양쪽 다 끊긴다
 * DELETE /api/v1/users/{userId}/block                    차단 해제 (멱등, 200) — 팔로우는 복구 안 한다
 * GET    /api/v1/users/{userId}/profile                  프로필 머리
 * GET    /api/v1/users/{userId}/stories?cursor=&limit=   그 사람의 기록(보이는 범위만)
 * GET    /api/v1/users/{userId}/followers?cursor=&limit= 그 사람을 팔로우하는 사람들
 * GET    /api/v1/users/{userId}/following?cursor=&limit= 그 사람이 팔로우하는 사람들
 * GET    /api/v1/users/{userId}/blocks?cursor=&limit=    내가 차단한 사람들 — userId 는 본인이어야 한다
 * </pre>
 *
 * <p>🔴 <b>차단의 방향.</b> A 가 B 를 차단하면 <b>B 가 A 를 못 본다.</b> A 는 B 를 계속 본다
 * (B 가 A 를 차단하지 않았다면). 반대로 짐작하기 쉬운 자리다 — {@link BlockService} 참고.
 *
 * <p>{@code PUT} 인 이유 — 팔로우는 "이 상태로 만들어 달라" 이지 "하나 더 만들어 달라" 가 아니다. 두 번 보내도
 * 결과가 같아야 하고 HTTP 에서 그 뜻의 동사가 PUT 이다.
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

	/**
	 * 차단한다 — S15P21E201-990. 🔴 <b>팔로우가 양쪽 다 끊기고 되돌아오지 않는다.</b>
	 *
	 * <p>{@code PUT} 인 이유는 팔로우와 같다 — "이 상태로 만들어 달라" 이고 두 번 보내도 결과가 같다.
	 */
	@PutMapping("/block")
	public ApiResponse<BlockResponse> block(@PathVariable UUID userId, Authentication authentication) {
		UUID me = AuthenticatedUsers.requireId(authentication);
		return ApiResponse.success(this.blockService.block(me, userId), requestId());
	}

	/** 차단을 푼다. 팔로우는 복구하지 않는다 — {@link BlockService#block} 의 설명 참고. */
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

	/** 팔로워 목록 — {@link FollowService#followers} 참고 (그 사람이 나를 차단했으면 403). */
	@GetMapping("/followers")
	public ApiResponse<RelationListResponse> followers(@PathVariable UUID userId,
			@RequestParam(value = "cursor", required = false) String cursor,
			@RequestParam(value = "limit", required = false) Integer limit,
			Authentication authentication) {
		UUID viewer = AuthenticatedUsers.requireId(authentication);
		return ApiResponse.success(this.followService.followers(viewer, userId, cursor, limit), requestId());
	}

	/** 팔로잉 목록 — {@link FollowService#following} 참고. */
	@GetMapping("/following")
	public ApiResponse<RelationListResponse> following(@PathVariable UUID userId,
			@RequestParam(value = "cursor", required = false) String cursor,
			@RequestParam(value = "limit", required = false) Integer limit,
			Authentication authentication) {
		UUID viewer = AuthenticatedUsers.requireId(authentication);
		return ApiResponse.success(this.followService.following(viewer, userId, cursor, limit), requestId());
	}

	/** 내가 차단한 사람 목록 — {@code userId} 는 반드시 본인이어야 한다({@link BlockService#myBlocks}). */
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
