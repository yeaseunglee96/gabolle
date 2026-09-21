package com.gabolle.backend.assistant.presentation.dto;

import java.util.Locale;

import com.gabolle.backend.assistant.domain.AssistantReply;

/**
 * 여행 도우미 응답 본문. 프런트엔드 AssistantAction 유니언과 필드 이름이 같아야 하고,
 * kind 는 소문자(navigate/phrase/help)로 내려간다.
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
