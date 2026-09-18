package com.gabolle.backend.assistant.domain;

/**
 * 자연어 여행 도우미가 답을 어떤 모양으로 주는지 — S15P21E201-802.
 *
 * <p>프런트엔드 {@code AssistantAction} 유니언(front/dev, {@code src/assistant/intent.ts})의
 * {@code kind} 판별자와 값이 같아야 한다. 여기서 이름을 바꾸면 화면이 못 알아본다.
 *
 * <p>🔴 MVP 범위는 은행 앱 챗봇처럼 "안내"뿐이다 — 일정을 대신 짜는 {@code PLAN} 은 없다.
 * AI 가 여행 조건을 만들어 채우지 않고, 관련 화면(예: 새 여행 만들기)으로 안내만 한다.
 */
public enum AssistantActionKind {
	PHRASE, NAVIGATE, HELP
}
