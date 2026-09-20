package com.gabolle.backend.assistant.presentation;

import java.util.List;
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
import com.gabolle.backend.assistant.domain.AssistantTurn;
import com.gabolle.backend.assistant.presentation.dto.AssistantMessageRequestDto;
import com.gabolle.backend.assistant.presentation.dto.AssistantMessageResponseDto;
import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.common.security.AuthenticatedUsers;

/**
 * 자연어 메시지 하나를 여행 도우미 답으로 바꾼다. 업체 API 키를 화면에 두면 브라우저에
 * 그대로 노출되므로 서버가 대신 부른다.
 *
 * 인가는 로그인한 사람이면 된다 — 메시지는 부르는 쪽이 준 값이고 우리 자원이 아니다.
 * 로그인을 요구하는 것은 우리 키로 남이 호출을 돌리는 것을 막기 위해서이고, 사용자 id 는
 * 사용자별 요청 빈도 제한에도 쓰인다.
 *
 * 답변 언어는 Accept-Language 헤더를 그대로 벤더에 전달해 맞춘다.
 */
@RestController
@RequestMapping("/api/v1/assistant")
@Profile({ "db", "dev" })
public class AssistantController {

	private final AssistantChatService assistantChatService;

	public AssistantController(AssistantChatService assistantChatService) {
		this.assistantChatService = assistantChatService;
	}

	/** 메시지가 비었거나 너무 길면 400, 요청이 너무 잦으면 429, 업체 호출이 실패하면 502 다. */
	@PostMapping("/messages")
	public ApiResponse<AssistantMessageResponseDto> messages(@RequestBody AssistantMessageRequestDto request,
			Authentication authentication,
			@RequestHeader(value = "Accept-Language", defaultValue = "ko") String language,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId) {

		UUID userId = AuthenticatedUsers.requireId(authentication);

		List<AssistantTurn> history = request.history() == null
				? List.of()
				: request.history().stream().map(turn -> new AssistantTurn(turn.role(), turn.text())).toList();

		AssistantReply reply = this.assistantChatService.chat(userId, request.message(), language, history,
				request.itineraryId(), request.dayIndex());

		return ApiResponse.success(AssistantMessageResponseDto.from(reply), resolveRequestId(requestId));
	}

	private String resolveRequestId(String requestId) {
		return requestId == null || requestId.isBlank() ? "req_" + UUID.randomUUID() : requestId;
	}
}
