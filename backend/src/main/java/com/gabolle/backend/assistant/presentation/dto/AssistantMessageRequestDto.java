package com.gabolle.backend.assistant.presentation.dto;

import java.util.List;

/**
 * POST /api/v1/assistant/messages 요청 본문 — S15P21E201-802.
 *
 * <p>{@code history} 는 화면이 들고 있는 최근 대화(사용자·도우미가 번갈아 한 줄씩)를 그대로
 * 실어 보낸 것이다 — 서버는 대화를 저장하지 않으므로 이 값이 없으면 맥락도 없다. 비우거나
 * 생략해도 된다.
 *
 * <p>{@code itineraryId}·{@code dayIndex} — S15P21E201-987. 화면이 "이 일정 보고 물어보기"
 * 같은 명시적인 자리에서만 채워 보낸다. 챗봇이 자유 텍스트에서 "그 조회를 할지"를 추측하게
 * 하지 않는다 — 동의 판정과 조회 여부가 화면의 명시적 요청 하나에 묶여야 사고가 안 난다.
 * 둘 다 없으면(기본값) 예전과 동일하게 일정을 전혀 참고하지 않는다.
 */
public record AssistantMessageRequestDto(String message, List<TurnDto> history, String itineraryId,
		Integer dayIndex) {

	public record TurnDto(String role, String text) {
	}
}
