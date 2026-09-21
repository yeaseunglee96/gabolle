package com.gabolle.backend.story.presentation;

import java.util.List;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.common.security.AuthenticatedUsers;
import com.gabolle.backend.story.application.StoryFeedService;
import com.gabolle.backend.story.application.StoryService;
import com.gabolle.backend.story.presentation.dto.StoryCreateRequest;
import com.gabolle.backend.story.presentation.dto.StoryFeedResponse;
import com.gabolle.backend.story.presentation.dto.StoryResponse;
import com.gabolle.backend.story.presentation.dto.StoryUpdateRequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 여행 기록 — 작성·조회·수정·삭제·피드.
 *
 * <p>사진은 먼저 {@code POST /api/v1/uploads/story-image} 로 올리고 받은 주소를 작성 본문에 싣는다.
 */
@RestController
@RequestMapping("/api/v1/stories")
@Profile({ "db", "dev" })
public class StoryController {

	/** 실제로 적용된 정렬을 싣는 응답 머리. 화면이 사용자에게 무엇을 보여주는 중인지 말할 근거다. */
	public static final String FEED_APPLIED_HEADER = "X-Feed-Applied";

	private final StoryService storyService;

	private final StoryFeedService feedService;

	public StoryController(StoryService storyService, StoryFeedService feedService) {
		this.storyService = storyService;
		this.feedService = feedService;
	}

	/**
	 * 기록 목록.
	 *
	 * <p>응답 머리에 {@code X-Feed-Applied} 로 <b>실제로 적용된 정렬</b>을 싣는다. 화면이 「맞춤 추천」
	 * 이라고 써 놓고 속으로는 인기순을 보여주는 일이 생기지 않게 하려는 것이다 — 본문(DTO)은
	 * 안 건드린다.
	 */
	@GetMapping
	public ResponseEntity<ApiResponse<StoryFeedResponse>> feed(
			@RequestParam(value = "scope", required = false, defaultValue = "ALL") StoryFeedService.Scope scope,
			@RequestParam(value = "sort", required = false, defaultValue = "RECENT") StoryFeedService.Sort sort,
			@RequestParam(value = "cursor", required = false) String cursor,
			@RequestParam(value = "limit", required = false) Integer limit,
			Authentication authentication) {
		// 피드는 로그인 없이도 볼 수 있다. 익명이면 viewer 가 null 이 되고 StoryFeedService 가 공개 기록만 내보낸다.
		UUID viewer = AuthenticatedUsers.optionalId(authentication).orElse(null);
		StoryFeedService.Feed feed = this.feedService.feed(viewer, scope, sort, cursor, limit);
		return ResponseEntity.ok().header(FEED_APPLIED_HEADER, feed.applied().name())
				.body(ApiResponse.success(feed.page(), requestId()));
	}

	@PostMapping
	public ResponseEntity<ApiResponse<StoryResponse>> create(@Valid @RequestBody StoryCreateRequest request,
			Authentication authentication) {
		UUID author = AuthenticatedUsers.requireId(authentication);
		StoryResponse body = this.storyService.create(author, request);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(body, requestId()));
	}

	/**
	 * 로그인 없이도 열린다 — 익명은 공개(PUBLIC) 글만 본다.
	 *
	 * <p>못 보는 글은 404 다. 없음·지움·나만 보기·팔로워 전용이 모두 같은 응답이어야 응답 차이로 그 글이
	 * 있다는 사실이 새지 않는다.
	 */
	@GetMapping("/{storyId}")
	public ApiResponse<StoryResponse> get(@PathVariable UUID storyId, Authentication authentication) {
		UUID viewer = AuthenticatedUsers.optionalId(authentication).orElse(null);
		// 조회수는 비회원도 센다. 익명 요청은 optionalId 가 비므로 익명 세션 id 를 함께 넘긴다 —
		// 둘 다 없으면 세지 않는다.
		UUID anonymousSessionId = AuthenticatedUsers.optionalAnonymousSessionId(authentication).orElse(null);
		return ApiResponse.success(this.storyService.get(storyId, viewer, anonymousSessionId), requestId());
	}

	/**
	 * 이 글의 공유 링크를 복사했다고 알린다. 복사는 앱 안에서 끝나는 행동이라 앱이 알려 주지 않으면 서버가 모른다.
	 *
	 * <p>응답이 204 가 아니라 그 글인 이유는, 앱이 상세를 다시 불러야 하면 그 호출이 조회수를 또 올리기 때문이다.
	 * 오늘 이미 센 사람이 다시 눌러 수가 안 올라도 200 이다. 로그인 없이도 열리고, 못 보는 글은 404 다.
	 */
	@PostMapping("/{storyId}/link-copies")
	public ApiResponse<StoryResponse> recordLinkCopy(@PathVariable UUID storyId, Authentication authentication) {
		UUID actor = AuthenticatedUsers.optionalId(authentication).orElse(null);
		UUID anonymousSessionId = AuthenticatedUsers.optionalAnonymousSessionId(authentication).orElse(null);
		return ApiResponse.success(this.storyService.recordLinkCopy(storyId, actor, anonymousSessionId), requestId());
	}

	/**
	 * 이 글에 직접 달린 댓글. 손자는 안 딸려 온다 — 어떤 댓글의 답글은 그 댓글 id 로 이 경로를 다시 부른다.
	 *
	 * <p>비회원도 부를 수 있고, 볼 수 없는 글이면 404 다.
	 */
	@GetMapping("/{storyId}/replies")
	public ApiResponse<List<StoryResponse>> replies(@PathVariable UUID storyId,
			@RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
			Authentication authentication) {
		UUID viewer = AuthenticatedUsers.optionalId(authentication).orElse(null);
		return ApiResponse.success(this.storyService.replies(storyId, viewer, limit), requestId());
	}

	@PatchMapping("/{storyId}")
	public ApiResponse<StoryResponse> update(@PathVariable UUID storyId,
			@Valid @RequestBody StoryUpdateRequest request, Authentication authentication) {
		UUID editor = AuthenticatedUsers.requireId(authentication);
		return ApiResponse.success(this.storyService.update(storyId, editor, request), requestId());
	}

	@DeleteMapping("/{storyId}")
	public ResponseEntity<Void> delete(@PathVariable UUID storyId, Authentication authentication) {
		UUID editor = AuthenticatedUsers.requireId(authentication);
		this.storyService.delete(storyId, editor);
		return ResponseEntity.noContent().build();
	}

	private static String requestId() {
		return "req_" + UUID.randomUUID();
	}
}
