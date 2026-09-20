package com.gabolle.backend.assistant.presentation.dto;

import java.util.List;

/**
 * 여행 도우미 요청 본문.
 *
 * history 는 화면이 들고 있는 최근 대화다. 서버가 대화를 저장하지 않으므로 이 값이 없으면
 * 맥락도 없다. 비우거나 생략해도 된다.
 *
 * itineraryId·dayIndex 는 화면이 명시적인 자리에서만 채워 보낸다 — 챗봇이 자유 텍스트에서
 * 일정 조회 여부를 추측하게 두지 않는다. 둘 다 없으면 일정을 전혀 참고하지 않는다.
 */
public record AssistantMessageRequestDto(String message, List<TurnDto> history, String itineraryId,
		Integer dayIndex) {

	public record TurnDto(String role, String text) {
	}
}
