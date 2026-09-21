package com.gabolle.backend.assistant.domain;

/**
 * 자연어 여행 도우미의 답 하나. kind 에 따라 채워지는 필드가 다르고 나머지는 전부 null 이다 —
 * PHRASE 는 korean·pronunciation, NAVIGATE 는 label·href, HELP 는 reply 뿐이다.
 */
public record AssistantReply(
		AssistantActionKind kind,
		String reply,
		String korean,
		String pronunciation,
		String label,
		String href) {
}
