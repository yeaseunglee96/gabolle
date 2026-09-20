package com.gabolle.backend.auth.api;

import com.gabolle.backend.auth.service.AnonymousSessionService;
import com.gabolle.backend.common.api.ApiResponse;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 익명 출입증 발급. 가입 안 한 사람도 여행을 만들 수 있는데, 출입증이 없으면 요청이 끝나는 순간
 * 만든 것의 주인을 잃는다. 다음부터는 {@code X-Session-Token} 헤더에 그 값을 실으면
 * {@link com.gabolle.backend.auth.config.AnonymousSessionAuthenticationFilter} 가 대조한다.
 *
 * <p>{@link AuthController} 에 넣지 않은 것은 그쪽 경로가 전부 실제 계정을 전제하기 때문이다.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Profile({"db", "dev"})
public class AnonymousAuthController {

	private final AnonymousSessionService anonymousSessionService;

	public AnonymousAuthController(AnonymousSessionService anonymousSessionService) {
		this.anonymousSessionService = anonymousSessionService;
	}

	@PostMapping("/anonymous")
	public ResponseEntity<ApiResponse<AnonymousSessionResponse>> issue(
			@RequestHeader(value = "X-Request-Id", required = false) String requestId) {
		AnonymousSessionResponse body = AnonymousSessionResponse.from(anonymousSessionService.issue());
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(body, resolveRequestId(requestId)));
	}

	private String resolveRequestId(String requestId) {
		return requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId;
	}
}
