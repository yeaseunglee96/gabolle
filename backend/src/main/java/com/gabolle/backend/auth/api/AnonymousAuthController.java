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
 * 익명 출입증 발급 — S15P21E201-303.
 *
 * <p>가입 안 한 사람도 여행을 만들 수 있는데, 세션 표가 없으면 그 요청이 끝나는 순간
 * 만든 것의 주인을 잃는다. 이 경로가 그 사람만의 무작위 출입증을 한 장 내주고, 다음부터는
 * {@code X-Session-Token} 헤더에 그 값을 실으면({@link com.gabolle.backend.auth.config.AnonymousSessionAuthenticationFilter}
 * 가 대조한다) 같은 세션의 주인으로 인식된다.
 *
 * <p>{@link AuthController} 에 넣지 않은 이유는 그쪽 경로 전부가 실제 계정(로컬·소셜)을
 * 전제하기 때문이다. 이 경로는 계정이 없는 사람을 위한 것이라 따로 둔다.
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
