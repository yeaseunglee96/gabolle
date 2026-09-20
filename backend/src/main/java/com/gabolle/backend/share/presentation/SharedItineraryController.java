package com.gabolle.backend.share.presentation;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.share.application.ShareLinkService;
import com.gabolle.backend.share.presentation.dto.SharedItineraryResponse;

/**
 * 읽기 전용 공유 조회. 인증을 받지 않는다 — 43글자 난수 token 이 유일한 잠금이고,
 * SecurityConfig 가 이 경로의 GET 을 열어 둔다.
 */
@RestController
@RequestMapping("/api/v1/shares")
@Profile({ "db", "dev" })
public class SharedItineraryController {

	private final ShareLinkService shareLinkService;

	public SharedItineraryController(ShareLinkService shareLinkService) {
		this.shareLinkService = shareLinkService;
	}

	@GetMapping("/{token}")
	public ApiResponse<SharedItineraryResponse> open(@PathVariable String token) {
		SharedItineraryResponse response = this.shareLinkService.open(token);
		return ApiResponse.success(response, "req_" + UUID.randomUUID());
	}
}
