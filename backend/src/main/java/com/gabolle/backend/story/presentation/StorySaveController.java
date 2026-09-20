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
 * 기록(글) 저장(북마크).
 *
 * <p>목록 경로를 {@code /me} 아래 둔 것은 남의 저장 목록에 닿을 길이 없어야 하기 때문이다. 켜기/끄기가
 * {@code PUT}/{@code DELETE} 인 이유는 켜짐/꺼짐이라 「두 번 켠 상태」가 없어서다.
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
