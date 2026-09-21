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
 * <p>둘 다 몇 번을 보내도 결과가 같다 — {@code PUT} 은 같은 값을 다시 보내도 같은 상태로 끝나고,
 * {@code DELETE} 는 안 눌렀던 것을 지워도 성공이다.
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
