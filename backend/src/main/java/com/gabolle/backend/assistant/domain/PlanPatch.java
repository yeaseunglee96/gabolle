package com.gabolle.backend.assistant.domain;

import java.util.List;

/**
 * AI 가 여행 초안({@code PlanDraft}, front/dev {@code src/plan/PlanProvider.tsx})에 제안하는
 * 변경분 — S15P21E201-802.
 *
 * <h2>🔴 알레르기·접근성 같은 안전 조건은 여기 없다</h2>
 * S15P21E201-628 의 완료 기준("안전 조건은 AI가 임의로 완화하지 않는다")을 프롬프트가 아니라
 * <b>타입</b>으로 지킨다 — {@code allergies}·{@code accessibilityNeeds}·{@code wheelchair}·
 * {@code stroller}·{@code luggage}·{@code dietTypes} 류는 이 레코드에 필드 자체가 없어서,
 * AI 가 무엇을 답하든 그 값이 응답에 실릴 자리가 없다. "AI 에게 건드리지 말라고 시켰다"
 * 보다 강한 보장이다.
 *
 * <p>모든 필드가 nullable 이다 — 사용자가 말하지 않은 것은 채우지 않는다({@code null} =
 * "이 값은 그대로 둔다"). 화면은 {@code Partial<PlanDraft>} 로 받아 null 이 아닌 것만 병합한다.
 */
public record PlanPatch(
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
}
