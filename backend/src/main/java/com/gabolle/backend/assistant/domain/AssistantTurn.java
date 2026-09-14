package com.gabolle.backend.assistant.domain;

/**
 * 이전 대화 한 줄 — S15P21E201-802 (대화 맥락).
 *
 * <p>{@code role} 은 {@code "user"} 또는 {@code "assistant"} 뿐이다. 화면이 이미 들고 있는
 * 메시지 목록을 그대로 실어 보내는 값이라, 서버는 이것을 저장하지 않는다(무상태) — 매
 * 요청마다 화면이 최근 몇 턴을 함께 보낸다.
 */
public record AssistantTurn(String role, String text) {
}
