package com.gabolle.backend.assistant.adapter;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Claude 구조화 출력({@code StructuredMessageCreateParams})이 실제로 채워 주는 모양 —
 * S15P21E201-802.
 *
 * <p>이 클래스가 {@link ClaudeAssistantAdapter} 밖으로 나가지 않는다 — SDK 가 이 타입에서 JSON
 * 스키마를 뽑아내야 해서 여기만 Jackson 애노테이션을 붙이고, 도메인({@code AssistantReply})은
 * 여전히 순수하게 둔다.
 *
 * <p>MVP 범위는 은행 앱 챗봇처럼 "안내"만 한다 — navigate/phrase/help 세 가지뿐이고, 일정을
 * 대신 짜는 {@code plan} 은 이 범위 밖이라 없다.
 */
record ClaudeStructuredReply(
		@JsonPropertyDescription("정확히 하나: navigate, phrase, help 중 소문자로") String kind,
		@JsonPropertyDescription("사용자에게 보여줄 한국어 답변 한두 문장") String reply,
		@JsonPropertyDescription("kind=phrase 일 때만: 원문 한국어 표현") String korean,
		@JsonPropertyDescription("kind=phrase 일 때만: 발음(한글 표기)") String pronunciation,
		@JsonPropertyDescription("kind=navigate 일 때만: 이동 버튼에 쓸 한국어 라벨") String label,
		@JsonPropertyDescription("kind=navigate 일 때만: '/plan/basic', '/trips', '/field/translate' 중 하나") String href) {
}
