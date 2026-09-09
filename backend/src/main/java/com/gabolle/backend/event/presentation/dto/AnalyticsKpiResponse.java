package com.gabolle.backend.event.presentation.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * {@code GET /api/v1/analytics/kpis} 응답 — S15P21E201-160 작업 내용 5번.
 *
 * <p>🔴 <b>"무엇을 지표로 삼을 것인가" 는 아직 팀 결정이 없다</b>(이 티켓 2026-09-03 코멘트).
 * 그 결정을 기다리며 엔드포인트 자체를 안 만들면, 결정이 나온 뒤에도 여전히 아무 응답이
 * 없다. 그래서 여기서는 <b>사업 판단이 필요 없는 값</b>만 낸다 — 이미 있는 이벤트를 종류별로
 * 센 것({@link #eventCounts()})과 Outbox 가 실제로 밀리고 있는가({@link #outboxHealth()}).
 * 둘 다 "몇 명이 그걸 왜 하는가" 가 아니라 "지금 데이터가 얼마나 쌓였고 잘 나가고 있는가"
 * 라서, 나중에 팀이 KPI 를 정해도 이 응답은 그 위에 얹이지 지워지지 않는다.
 */
public record AnalyticsKpiResponse(
		OffsetDateTime from,
		OffsetDateTime to,
		List<EventTypeCountEntry> eventCounts,
		OutboxHealthEntry outboxHealth) {

	public record EventTypeCountEntry(String eventType, long count) {
	}

	/**
	 * @param pendingCount 지금 이 순간 아직 안 보낸 건수 — {@code from}~{@code to} 와 무관한
	 *     실시간 값이다. 기간을 좁혀도 이 수는 안 바뀐다 — "밀린 게 있는가" 는 항상 지금 묻는
	 *     질문이기 때문이다.
	 * @param oldestPendingAgeSeconds 제일 오래 밀린 건이 들어온 지 몇 초 됐는가. 밀린 게
	 *     하나도 없으면 {@code null} — 0 을 쓰면 "방금 밀리기 시작했다" 와 구분이 안 된다.
	 * @param publishedCount {@code from}~{@code to} 사이에 실제로 나간 건수.
	 * @param failedCount 지금 이 순간 재시도해도 계속 실패 중인 건수 — 실시간 값이다.
	 */
	public record OutboxHealthEntry(
			long pendingCount,
			Long oldestPendingAgeSeconds,
			long publishedCount,
			long failedCount) {
	}
}
