package com.gabolle.backend.assistant.adapter;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Claude 구조화 출력({@code StructuredMessageCreateParams})이 실제로 채워 주는 모양 —
 * S15P21E201-802.
 *
 * <p>이 클래스가 {@link ClaudeAssistantAdapter} 밖으로 나가지 않는다 — SDK 가 이 타입에서 JSON
 * 스키마를 뽑아내야 해서 여기만 Jackson 애노테이션을 붙이고, 도메인({@code AssistantReply})은
 * 여전히 순수하게 둔다.
 *
 * <p>🔴 안전 필드(알레르기·접근성 등)는 여기 필드 자체가 없다 — {@link ClaudeStructuredPlanPatch}
 * 참고.
 */
record ClaudeStructuredReply(
		@JsonPropertyDescription("정확히 하나: plan, phrase, navigate, help 중 소문자로") String kind,
		@JsonPropertyDescription("사용자에게 보여줄 한국어 답변 한두 문장") String reply,
		@JsonPropertyDescription("kind=plan 일 때만: 실제로 반영한 항목을 짧은 한국어 문장으로 나열. "
				+ "그 외 kind 에는 빈 배열") List<String> summary,
		@JsonPropertyDescription("kind=plan 일 때만 채운다. 그 외 kind 에는 null") ClaudeStructuredPlanPatch patch,
		@JsonPropertyDescription("kind=phrase 일 때만: 원문 한국어 표현") String korean,
		@JsonPropertyDescription("kind=phrase 일 때만: 발음(한글 표기)") String pronunciation,
		@JsonPropertyDescription("kind=navigate 일 때만: 이동 버튼에 쓸 한국어 라벨") String label,
		@JsonPropertyDescription("kind=navigate 일 때만: '/field/translate' 또는 '/trips' 중 하나") String href) {

	/**
	 * S15P21E201-802 안전 부분 — 대화 도중 언급된 알레르기·접근성·휠체어·유모차·짐·식단 제약은
	 * 여기 어떤 필드에도 담기지 않는다. {@code PlanDraft}(front/dev) 의 안전 관련 필드
	 * ({@code allergies}·{@code accessibilityNeeds}·{@code wheelchair}·{@code stroller}·
	 * {@code luggage}·{@code dietTypes} 등)는 이 레코드에 대응하는 자리가 없다.
	 */
	record ClaudeStructuredPlanPatch(
			@JsonPropertyDescription("언급 있으면 YYYY-MM-DD, 없으면 null") String startDate,
			@JsonPropertyDescription("언급 있으면 YYYY-MM-DD, 없으면 null") String endDate,
			@JsonPropertyDescription("언급 있으면 총 인원, 없으면 null") Integer travelers,
			@JsonPropertyDescription("언급 있으면 성인 수, 없으면 null") Integer adults,
			@JsonPropertyDescription("언급 있으면 아동 수, 없으면 null") Integer children,
			@JsonPropertyDescription("언급 있으면 지역 이름 목록(예: 해운대, 광안리), 없으면 null") List<String> travelAreas,
			@JsonPropertyDescription("언급 있으면 음식 취향 키워드 목록, 없으면 null") List<String> foods,
			@JsonPropertyDescription("언급 있으면 분위기 키워드 목록, 없으면 null") List<String> atmospheres,
			@JsonPropertyDescription("언급 있으면 TRANSIT, WALK, CAR 중 하나, 없으면 null") String transport,
			@JsonPropertyDescription("언급 있으면 원화 예산, 없으면 null") Integer budgetKrw) {
	}
}
