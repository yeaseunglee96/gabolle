package com.gabolle.backend.assistant.domain;

import java.util.List;

/**
 * 벤더(Gemini)에 보낼 대화 요청 하나 — S15P21E201-802.
 *
 * <p>{@code history} 는 이미 {@link com.gabolle.backend.assistant.application.AssistantChatService}
 * 가 길이·개수를 다듬어 둔 상태다 — 어댑터는 그대로 벤더 API 모양으로 옮기기만 한다.
 *
 * <p>{@code tripContext} — S15P21E201-987. 사용자가 동의하고 명시적으로 요청했을 때만
 * 채워지는, 실제 일정 하루치를 사람이 읽을 수 있게 간추린 텍스트다({@code null} 이면 예전과
 * 같다). 건강·식이 제약 같은 민감정보는 여기 절대 안 들어간다 — 애초에 이 값을 만드는 쪽이
 * 그 항목을 참조하지 않는다.
 */
public record AssistantChatRequest(String message, String language, List<AssistantTurn> history,
		String tripContext) {
}
