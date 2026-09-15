package com.gabolle.backend.story.presentation;

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

/**
 * 여행 기록 — 작성·조회·수정·삭제·피드. S15P21E201-207 · -221 · -226 · -233 · -123.
 *
 * <pre>
 * GET    /api/v1/stories?scope=ALL|FOLLOWING&cursor=&limit=   피드
 * POST   /api/v1/stories                                       작성 (201)
 * GET    /api/v1/stories/{storyId}                             상세
 * PATCH  /api/v1/stories/{storyId}                             수정 (작성자만)
 * DELETE /api/v1/stories/{storyId}                             삭제 (작성자만, 204)
 * </pre>
 *
 * <p>사진은 먼저 {@code POST /api/v1/uploads/story-image} 로 올리고 받은 주소를 작성 본문에 싣는다.
 * 요청자는 언제나 {@link AuthenticatedUsers#requireId} — 헤더로 사용자를 받지 않는다.
 */
@RestController
@RequestMapping("/api/v1/stories")
@Profile({ "db", "dev" })
public class StoryController {

	private final StoryService storyService;

	private final StoryFeedService feedService;

	public StoryController(StoryService storyService, StoryFeedService feedService) {
		this.storyService = storyService;
		this.feedService = feedService;
	}

	@GetMapping
	public ApiResponse<StoryFeedResponse> feed(
			@RequestParam(value = "scope", required = false, defaultValue = "ALL") StoryFeedService.Scope scope,
			@RequestParam(value = "cursor", required = false) String cursor,
			@RequestParam(value = "limit", required = false) Integer limit,
			Authentication authentication) {
		// 🔴 S15P21E201-974 — 여기만 requireId 가 아니라 optionalId 다. 피드는 로그인 없이도
		//    볼 수 있어야 한다(제품 결정). 익명 출입증만 들고 오면 viewer 가 null 이 되고,
		//    StoryFeedService 가 그때 공개 기록만 내보낸다.
		//
		//    🔴 막고 있던 것이 SecurityConfig 가 아니라 이 한 줄이었다. 익명 필터가 심는
		//    권한으로 anyRequest().authenticated() 는 이미 통과한다(장소 API 가 익명으로
		//    200 이 나오는 이유). 그 뒤 requireId 가 principal "anon:<세션id>" 를 UUID 로
		//    못 읽어 401 을 던지고 있었다.
		UUID viewer = AuthenticatedUsers.optionalId(authentication).orElse(null);
		return ApiResponse.success(this.feedService.feed(viewer, scope, cursor, limit), requestId());
	}

	@PostMapping
	public ResponseEntity<ApiResponse<StoryResponse>> create(@Valid @RequestBody StoryCreateRequest request,
			Authentication authentication) {
		UUID author = AuthenticatedUsers.requireId(authentication);
		StoryResponse body = this.storyService.create(author, request);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(body, requestId()));
	}

	@GetMapping("/{storyId}")
	public ApiResponse<StoryResponse> get(@PathVariable UUID storyId, Authentication authentication) {
		UUID viewer = AuthenticatedUsers.requireId(authentication);
		return ApiResponse.success(this.storyService.get(storyId, viewer), requestId());
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
