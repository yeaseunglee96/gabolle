package com.gabolle.backend.assistant.presentation;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.assistant.application.AssistantChatService;
import com.gabolle.backend.assistant.domain.AssistantReply;
import com.gabolle.backend.assistant.presentation.dto.AssistantMessageRequestDto;
import com.gabolle.backend.assistant.presentation.dto.AssistantMessageResponseDto;
import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.common.security.AuthenticatedUsers;

/**
 * 자연어 메시지 하나를 여행 도우미 답으로 바꾼다 — S15P21E201-802 (S15P21E201-628 계약).
 *
 * <h2>왜 서버가 대신 부르는가</h2>
 * {@code TranslateController} 와 같은 이유다 — Claude API 키를 화면에 두면 브라우저에 그대로
 * 노출된다.
 *
 * <h2>🔴 인가는 "로그인한 사람이면 된다" 다</h2>
 * {@code TranslateController} 와 같은 이유 — 메시지는 부르는 쪽이 준 값이고 우리 자원이 아니다.
 * 로그인을 요구하는 것은 우리 업체 키로 남이 대신 호출을 돌리는 것(비용)을 막기 위해서다.
 */
@RestController
@RequestMapping("/api/v1/assistant")
@Profile({ "db", "dev" })
public class AssistantController {

	private final AssistantChatService assistantChatService;

	public AssistantController(AssistantChatService assistantChatService) {
		this.assistantChatService = assistantChatService;
	}

	/**
	 * @throws IllegalArgumentException 메시지가 비었거나 너무 길다 — 400
	 * @throws com.gabolle.backend.assistant.application.AssistantVendorException 업체 호출 실패 — 502
	 */
	@PostMapping("/messages")
	public ApiResponse<AssistantMessageResponseDto> messages(@RequestBody AssistantMessageRequestDto request,
			Authentication authentication,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId) {

		AuthenticatedUsers.requireId(authentication);

		AssistantReply reply = this.assistantChatService.chat(request.message());

		return ApiResponse.success(AssistantMessageResponseDto.from(reply), resolveRequestId(requestId));
	}

	private String resolveRequestId(String requestId) {
		return requestId == null || requestId.isBlank() ? "req_" + UUID.randomUUID() : requestId;
	}
}
