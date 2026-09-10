package com.gabolle.backend.assistant.domain;

import java.util.List;

/**
 * 자연어 여행 도우미의 답 하나 — S15P21E201-802.
 *
 * <p>{@code kind} 에 따라 어느 필드가 채워지는지가 다르다 (프런트 {@code AssistantAction} 과 같은
 * 판별 유니언 모양을 필드 하나짜리 레코드로 편 것):
 * <ul>
 *   <li>{@code PLAN} — {@code summary}·{@code patch}</li>
 *   <li>{@code PHRASE} — {@code korean}·{@code pronunciation}</li>
 *   <li>{@code NAVIGATE} — {@code label}·{@code href}</li>
 *   <li>{@code HELP} — {@code reply} 뿐</li>
 * </ul>
 * 해당하지 않는 필드는 전부 {@code null}(또는 빈 리스트)이다.
 */
public record AssistantReply(
		AssistantActionKind kind,
		String reply,
		List<String> summary,
		PlanPatch patch,
		String korean,
		String pronunciation,
		String label,
		String href) {
}
