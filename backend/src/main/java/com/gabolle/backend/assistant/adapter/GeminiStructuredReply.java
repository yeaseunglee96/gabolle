package com.gabolle.backend.assistant.adapter;

import java.util.List;

/**
 * Gemini 구조화 출력이 채워 주는 모양. GeminiAssistantAdapter.RESPONSE_SCHEMA 와 같은
 * 모양이어야 한다.
 *
 * 이 클래스는 어댑터 밖으로 나가지 않는다 — 도메인(AssistantReply)은 순수하게 둔다.
 *
 * <p>areas·categories·startDate 는 S15P21E201-1825 에서 더했다 — 「광안리 맛집 2명」을 말해도
 * 「새 여행 만들기」 버튼에 일수·인원만 실려 지역·취향이 사라졌다.
 */
record GeminiStructuredReply(
		String kind,
		String reply,
		String korean,
		String pronunciation,
		String label,
		String href,
		Integer days,
		Integer people,
		List<String> areas,
		List<String> categories,
		String startDate) {

	/** 지역·취향·출발일이 없는 예전 모양 — 기존 시험과 호출부가 그대로 쓴다. */
	GeminiStructuredReply(String kind, String reply, String korean, String pronunciation, String label, String href,
			Integer days, Integer people) {
		this(kind, reply, korean, pronunciation, label, href, days, people, null, null, null);
	}
}
