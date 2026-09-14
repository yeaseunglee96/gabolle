package com.gabolle.backend.assistant.domain;

import java.util.List;

/**
 * 벤더(Gemini)에 보낼 대화 요청 하나 — S15P21E201-802.
 *
 * <p>{@code history} 는 이미 {@link com.gabolle.backend.assistant.application.AssistantChatService}
 * 가 길이·개수를 다듬어 둔 상태다 — 어댑터는 그대로 벤더 API 모양으로 옮기기만 한다.
 */
public record AssistantChatRequest(String message, String language, List<AssistantTurn> history) {
}
