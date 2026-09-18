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
 * 여행 기록 — 작성·조회·수정·삭제·피드. S15P21E201-207 · -221 · -226 · -233 · -123.
 *
 * <pre>
 * GET    /api/v1/stories?scope=ALL|FOLLOWING&cursor=&limit=   피드
 * POST   /api/v1/stories                                       작성 (201)
 * GET    /api/v1/stories/{storyId}                             상세
 * PATCH  /api/v1/stories/{storyId}                             수정 (작성자만)
 * DELETE /api/v1/stories/{storyId}                             삭제 (작성자만, 204)
 * POST   /api/v1/stories/{storyId}/link-copies                 링크를 복사했다고 알린다
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

	/**
	 * 🔴 S15P21E201-995 — 피드와 마찬가지로 <b>로그인 없이도 열린다.</b> 익명은 공개(PUBLIC)
	 * 글만 본다 ({@code StoryVisibilityPolicy.canView} 의 첫 분기).
	 *
	 * <p>974 가 목록을 열었는데 여기가 막혀 있어서 <b>피드는 보이는데 카드를 누르면 401</b> 인
	 * 상태였다. 반쯤 열린 문이라 목록을 연 의미가 절반만 살았다.
	 *
	 * <p>못 보는 글은 <b>404</b> 다 — 403 도 401 도 아니다. 없음·지움·나만 보기·팔로워 전용이
	 * 전부 같은 응답이어야 한다({@link StoryExceptionHandler} 주석). 응답이 갈리면 그 차이가
	 * 곧 "그 글이 있다" 는 사실의 유출이다. 익명이 찔러도 <b>로그인한 남이 찌른 것과 똑같이</b>
	 * 404 가 나간다.
	 */
	@GetMapping("/{storyId}")
	public ApiResponse<StoryResponse> get(@PathVariable UUID storyId, Authentication authentication) {
		UUID viewer = AuthenticatedUsers.optionalId(authentication).orElse(null);
		// 🔴 S15P21E201-1204 — 조회수는 비회원도 센다. optionalId 는 익명 요청에 빈 값을 주므로
		//    (principal 이 "anon:" 형식이라 UUID 로 안 읽힌다) 익명 세션 id 를 따로 꺼내 함께 넘긴다.
		//    둘 다 없으면 셀 수 없다 — 그 판단은 StoryService.recordView 가 한다.
		UUID anonymousSessionId = AuthenticatedUsers.optionalAnonymousSessionId(authentication).orElse(null);
		return ApiResponse.success(this.storyService.get(storyId, viewer, anonymousSessionId), requestId());
	}

	/**
	 * 이 글의 공유 링크를 복사했다고 알린다 — S15P21E201-1215.
	 *
	 * <h2>🔴 왜 조회수처럼 묻어 가지 못하나</h2>
	 *
	 * 조회수는 상세 조회에 묻어 간다 — 글을 여는 행동이 이미 서버를 부르기 때문이다. <b>복사는
	 * 앱 안에서 끝나는 행동</b>이라 서버를 부르는 자리가 없다. 앱이 알려 주지 않으면 서버는
	 * 그 일이 있었는지 영영 모른다. 그래서 이 경로가 따로 있다.
	 *
	 * <h2>🔴 응답은 204 가 아니라 그 글이다</h2>
	 *
	 * 화면이 눌린 그 자리에서 인용수를 새 숫자로 바꿔 그려야 하는데, 204 로 답하면 앱이
	 * <b>상세를 한 번 더 불러야</b> 한다. 그 한 번이 또 조회수를 올린다 — 복사 버튼을 누른 것이
	 * 조회로 세어지는 셈이다. 지금 모습을 그대로 돌려주면 그 왕복이 아예 없다.
	 *
	 * <h2>🔴 수가 안 올라도 200 이다</h2>
	 *
	 * 오늘 이미 센 사람이 또 눌러도, 작성자 본인이 자기 글 링크를 복사해도 <b>복사 자체는
	 * 정상으로 일어난 일</b>이다. 앱이 거절로 읽고 사용자에게 오류를 보여줄 이유가 없다.
	 * 돌아온 {@code linkCopyCount} 를 그대로 그리면 된다.
	 *
	 * <p>피드·상세와 마찬가지로 <b>로그인 없이도 열린다</b> — 비회원의 복사도 센다(익명 세션으로
	 * 식별되는 경우만). 못 보는 글은 그 글 조회와 같은 이유로 <b>404</b> 다.
	 */
	@PostMapping("/{storyId}/link-copies")
	public ApiResponse<StoryResponse> recordLinkCopy(@PathVariable UUID storyId, Authentication authentication) {
		UUID actor = AuthenticatedUsers.optionalId(authentication).orElse(null);
		UUID anonymousSessionId = AuthenticatedUsers.optionalAnonymousSessionId(authentication).orElse(null);
		return ApiResponse.success(this.storyService.recordLinkCopy(storyId, actor, anonymousSessionId), requestId());
	}

	/**
	 * 이 글에 <b>직접</b> 달린 댓글 — S15P21E201-1183.
	 *
	 * <p>응답은 {@link StoryResponse} 목록이다. <b>원글과 같은 모양</b>이라 화면이 같은 부품으로
	 * 그린다 — 시안이 요구한 그대로다.
	 *
	 * <p>🔴 손자는 안 딸려 온다. 어떤 댓글의 답글을 보려면 <b>그 댓글의 id 로 이 경로를 다시</b>
	 * 부른다. 한 번에 전부 내려주면 깊은 가지 하나 때문에 응답이 통째로 커지고, 화면은 대개
	 * 두 단만 펼친다.
	 *
	 * <p>비회원도 부를 수 있다 — 공개 글의 댓글은 로그인 없이 보인다. 볼 수 없는 글이면
	 * 그 글 조회와 같은 이유로 404 다.
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
