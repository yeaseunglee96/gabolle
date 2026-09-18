package com.gabolle.backend.story.presentation;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.security.AuthenticatedUsers;
import com.gabolle.backend.story.application.StoryReactionService;
import com.gabolle.backend.story.presentation.dto.StoryReactionRequest;

import jakarta.validation.Valid;

/**
 * 기록(글)에 좋아요·싫어요를 단다.
 *
 * <pre>
 * PUT    /api/v1/stories/{storyId}/reaction   {"reaction":"LIKE"}
 * DELETE /api/v1/stories/{storyId}/reaction
 * </pre>
 *
 * <h2>🔴 둘 다 몇 번을 보내도 결과가 같다</h2>
 *
 * {@code PUT} 은 같은 값을 다시 보내도 같은 상태로 끝나고, {@code DELETE} 는 안 눌렀던 것을
 * 지워도 성공이다. 앱이 재시도해도 사용자에게 오류가 안 뜬다 — {@code saved_place} 의
 * 하트가 정한 규칙을 그대로 따른다(S15P21E201-1013 · -1037).
 *
 * <p>🔴 요청자는 언제나 인증에서 읽는다. 경로에 남의 번호를 넣을 자리가 없다.
 */
@RestController
@RequestMapping("/api/v1/stories/{storyId}/reaction")
@Profile({ "db", "dev" })
public class StoryReactionController {

	private final StoryReactionService service;

	public StoryReactionController(StoryReactionService service) {
		this.service = service;
	}

	@PutMapping
	public ResponseEntity<Void> react(@PathVariable UUID storyId,
			@Valid @RequestBody StoryReactionRequest request, Authentication authentication) {

		UUID userId = AuthenticatedUsers.requireId(authentication);
		this.service.react(userId, storyId, request.reaction());
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping
	public ResponseEntity<Void> remove(@PathVariable UUID storyId, Authentication authentication) {
		UUID userId = AuthenticatedUsers.requireId(authentication);
		this.service.remove(userId, storyId);
		return ResponseEntity.noContent().build();
	}
}
