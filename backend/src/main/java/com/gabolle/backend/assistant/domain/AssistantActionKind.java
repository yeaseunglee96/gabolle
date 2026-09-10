package com.gabolle.backend.assistant.domain;

/**
 * 자연어 여행 도우미가 답을 어떤 모양으로 주는지 — S15P21E201-802.
 *
 * <p>프런트엔드 {@code AssistantAction} 유니언(front/dev, {@code src/assistant/intent.ts})의
 * {@code kind} 판별자와 값이 같아야 한다. 여기서 이름을 바꾸면 화면이 못 알아본다.
 */
public enum AssistantActionKind {
	PLAN, PHRASE, NAVIGATE, HELP
}
