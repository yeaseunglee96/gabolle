package com.gabolle.backend.assistant.presentation.dto;

import java.util.Locale;

import com.gabolle.backend.assistant.domain.AssistantReply;

/**
 * POST /api/v1/assistant/messages 응답 본문 — S15P21E201-802.
 *
 * <p>프런트엔드 {@code AssistantAction} 유니언(front/dev {@code src/assistant/intent.ts})과
 * 필드 이름이 같아야 한다 — {@code kind} 는 소문자(navigate/phrase/help)로 내려간다.
 */
public record AssistantMessageResponseDto(
		String kind,
		String reply,
		String korean,
		String pronunciation,
		String label,
		String href) {

	public static AssistantMessageResponseDto from(AssistantReply reply) {
		return new AssistantMessageResponseDto(
				reply.kind().name().toLowerCase(Locale.ROOT),
				reply.reply(),
				reply.korean(),
				reply.pronunciation(),
				reply.label(),
				reply.href());
	}
}
