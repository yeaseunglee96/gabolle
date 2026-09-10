package com.gabolle.backend.assistant.presentation.dto;

import java.util.List;
import java.util.Locale;

import com.gabolle.backend.assistant.domain.AssistantReply;
import com.gabolle.backend.assistant.domain.PlanPatch;

/**
 * POST /api/v1/assistant/messages 응답 본문 — S15P21E201-802.
 *
 * <p>프런트엔드 {@code AssistantAction} 유니언(front/dev {@code src/assistant/intent.ts})과
 * 필드 이름이 같아야 한다 — {@code kind} 는 소문자(plan/phrase/navigate/help)로 내려간다.
 */
public record AssistantMessageResponseDto(
		String kind,
		String reply,
		List<String> summary,
		PlanPatchDto patch,
		String korean,
		String pronunciation,
		String label,
		String href) {

	public static AssistantMessageResponseDto from(AssistantReply reply) {
		return new AssistantMessageResponseDto(
				reply.kind().name().toLowerCase(Locale.ROOT),
				reply.reply(),
				reply.summary(),
				PlanPatchDto.from(reply.patch()),
				reply.korean(),
				reply.pronunciation(),
				reply.label(),
				reply.href());
	}

	public record PlanPatchDto(
			String startDate,
			String endDate,
			Integer travelers,
			Integer adults,
			Integer children,
			List<String> travelAreas,
			List<String> foods,
			List<String> atmospheres,
			String transport,
			Integer budgetKrw) {

		static PlanPatchDto from(PlanPatch patch) {
			if (patch == null) {
				return null;
			}
			return new PlanPatchDto(patch.startDate(), patch.endDate(), patch.travelers(), patch.adults(),
					patch.children(), patch.travelAreas(), patch.foods(), patch.atmospheres(), patch.transport(),
					patch.budgetKrw());
		}
	}
}
