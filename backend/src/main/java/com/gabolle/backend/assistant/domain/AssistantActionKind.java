package com.gabolle.backend.assistant.domain;

/**
 * 자연어 여행 도우미가 답을 어떤 모양으로 주는지. 프런트엔드 AssistantAction 유니언의 kind
 * 판별자와 값이 같아야 한다 — 이름을 바꾸면 화면이 못 알아본다.
 *
 * 안내만 하므로 일정을 대신 짜는 PLAN 은 없다.
 */
public enum AssistantActionKind {
	PHRASE, NAVIGATE, HELP
}
