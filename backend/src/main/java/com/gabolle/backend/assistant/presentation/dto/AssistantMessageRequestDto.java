package com.gabolle.backend.assistant.presentation.dto;

import java.util.List;

/**
 * POST /api/v1/assistant/messages 요청 본문 — S15P21E201-802.
 *
 * <p>{@code history} 는 화면이 들고 있는 최근 대화(사용자·도우미가 번갈아 한 줄씩)를 그대로
 * 실어 보낸 것이다 — 서버는 대화를 저장하지 않으므로 이 값이 없으면 맥락도 없다. 비우거나
 * 생략해도 된다.
 */
public record AssistantMessageRequestDto(String message, List<TurnDto> history) {

	public record TurnDto(String role, String text) {
	}
}
