package com.gabolle.backend.assistant.adapter;

/**
 * Gemini 구조화 출력(JSON 스키마 강제)이 실제로 채워 주는 모양 — S15P21E201-802.
 *
 * <p>이 클래스가 {@link GeminiAssistantAdapter} 밖으로 나가지 않는다 — 도메인
 * ({@code AssistantReply})은 여전히 순수하게 둔다.
 *
 * <p>스키마 자체는 {@link GeminiAssistantAdapter#RESPONSE_SCHEMA}(JSON 스키마 Map)가 요청에
 * 실어 보낸다. 이 record 는 그 스키마와 같은 모양의 응답 JSON 을 파싱만 한다 — Claude 때와
 * 달리 SDK 가 POJO 로 직접 바인딩해 주지 않아서, 응답 원문(JSON 문자열)을 Jackson 으로
 * 수동 파싱한다({@code TranslationVendorAdapter} 가 벤더 응답을 파싱하는 것과 같은 자리다).
 *
 * <p>MVP 범위는 은행 앱 챗봇처럼 "안내"뿐이다 — navigate/phrase/help 세 가지뿐이고, 일정을
 * 대신 짜는 {@code plan} 은 없다.
 */
record GeminiStructuredReply(
		String kind,
		String reply,
		String korean,
		String pronunciation,
		String label,
		String href) {
}
