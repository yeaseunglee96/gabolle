package com.gabolle.backend.assistant.adapter;

/**
 * Gemini 구조화 출력이 채워 주는 모양. GeminiAssistantAdapter.RESPONSE_SCHEMA 와 같은
 * 모양이어야 한다.
 *
 * 이 클래스는 어댑터 밖으로 나가지 않는다 — 도메인(AssistantReply)은 순수하게 둔다.
 */
record GeminiStructuredReply(
		String kind,
		String reply,
		String korean,
		String pronunciation,
		String label,
		String href,
		Integer days,
		Integer people) {
}
