package com.gabolle.backend.assistant.domain;

import java.util.List;

/**
 * 벤더에 보낼 대화 요청 하나. history 는 AssistantChatService 가 길이·개수를 이미 다듬어 둔
 * 값이라 어댑터는 벤더 API 모양으로 옮기기만 한다.
 *
 * tripContext 는 사용자가 동의하고 명시적으로 요청했을 때만 채워지는 하루치 일정 요약이고,
 * 없으면 null 이다. 건강·식이 제약은 여기 들어가지 않는다 — 만드는 쪽이 아예 참조하지 않는다.
 */
public record AssistantChatRequest(String message, String language, List<AssistantTurn> history,
		String tripContext) {
}
