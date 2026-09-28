package com.gabolle.backend.story.presentation;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.common.security.AuthenticatedUsers;
import com.gabolle.backend.story.application.MyRepliesService;
import com.gabolle.backend.story.presentation.dto.MyRepliesResponse;

/**
 * 마이페이지 「내 댓글」 (S15P21E201-1600). 경로에 사용자 번호를 넣을 자리가 없다({@code /me}) — 대상은 인증 주체에서만
 * 읽어 남의 댓글 목록을 부를 수 없다.
 *
 * <p>{@code /api/v1/users/{userId}} 아래({@link UserSocialController})와 모양이 겹치지만 그쪽에 {@code /replies} 가
 * 없어 헷갈릴 자리가 없다.
 */
@RestController
@RequestMapping("/api/v1/users/me")
@Profile({ "db", "dev" })
public class MyRepliesController {

	private final MyRepliesService service;

	public MyRepliesController(MyRepliesService service) {
		this.service = service;
	}

	/** @param size 한 쪽 크기. 안 주면 20, 많아도 50 — 다른 피드와 같다 */
	@GetMapping("/replies")
	public ApiResponse<MyRepliesResponse> replies(@RequestParam(value = "cursor", required = false) String cursor,
			@RequestParam(value = "size", required = false) Integer size, Authentication authentication) {
		UUID me = AuthenticatedUsers.requireId(authentication);
		return ApiResponse.success(this.service.list(me, cursor, size), "req_" + UUID.randomUUID());
	}
}
