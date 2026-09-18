package com.gabolle.backend.story.presentation;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.common.security.AuthenticatedUsers;
import com.gabolle.backend.story.application.StorySaveService;
import com.gabolle.backend.story.presentation.dto.StorySaveResponse;

/**
 * 기록(글) 저장(북마크) — 사용자 리포트: "마이페이지에 저장 누르면 저장했던 피드들 뜨게".
 *
 * <pre>
 * GET    /api/v1/me/saved-stories        내가 저장한 것 전부 (최근 순)
 * PUT    /api/v1/stories/{storyId}/save  저장을 켠다
 * DELETE /api/v1/stories/{storyId}/save  저장을 끈다
 * </pre>
 *
 * <p>목록 경로는 {@code /me} 아래 둔다 — {@code SavedPlaceController}와 같은 이유로, 남의
 * 저장 목록에 닿을 길이 없어야 한다. 켜기/끄기는 {@code StoryReactionController}와 같은
 * 모양으로 {@code /stories/{storyId}} 아래 둔다 — 글 하나를 지목해 여는 경로다.
 *
 * <p>PUT/DELETE인 이유는 켜짐/꺼짐이라 "두 번 켠 상태"가 없어서다 — {@code
 * SavedPlaceController} 상단 주석 참고.
 */
@RestController
@Profile({ "db", "dev" })
public class StorySaveController {

	private final StorySaveService service;

	public StorySaveController(StorySaveService service) {
		this.service = service;
	}

	@GetMapping("/api/v1/me/saved-stories")
	public ApiResponse<StorySaveResponse.Page> list(Authentication authentication) {
		UUID userId = AuthenticatedUsers.requireId(authentication);
		return ApiResponse.success(StorySaveResponse.Page.of(this.service.list(userId)), "req_" + UUID.randomUUID());
	}

	@PutMapping("/api/v1/stories/{storyId}/save")
	public ResponseEntity<Void> save(@PathVariable UUID storyId, Authentication authentication) {
		UUID userId = AuthenticatedUsers.requireId(authentication);
		this.service.save(userId, storyId);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/api/v1/stories/{storyId}/save")
	public ResponseEntity<Void> remove(@PathVariable UUID storyId, Authentication authentication) {
		UUID userId = AuthenticatedUsers.requireId(authentication);
		this.service.remove(userId, storyId);
		return ResponseEntity.noContent().build();
	}
}
