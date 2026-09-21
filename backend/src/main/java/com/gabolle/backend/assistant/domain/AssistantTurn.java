package com.gabolle.backend.assistant.domain;

/**
 * 이전 대화 한 줄. role 은 "user" 또는 "assistant" 뿐이다. 서버는 저장하지 않고 매 요청마다
 * 화면이 최근 몇 턴을 함께 보낸다.
 */
public record AssistantTurn(String role, String text) {
}
