package com.gabolle.backend.event.presentation.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * {@code GET /api/v1/admin/analytics/kpis} 응답 — S15P21E201-160 작업 내용 5번.
 *
 * <p>🔴 <b>2026-09-15 갱신</b> — 이 티켓 2026-09-03 코멘트가 남겨 둔 "사업 KPI" 결정을 팀이
 * 이제 정했다: 추천 요청의 성공률·평균 처리 시간·실패 사유 분포({@link #recommendationJobHealth()}).
 * 그전까지는 사업 판단이 필요 없는 값만 냈다 — 이미 있는 이벤트를 종류별로 센 것
 * ({@link #eventCounts()})과 Outbox 가 실제로 밀리고 있는가({@link #outboxHealth()}). 이번에
 * 더한 값은 그 위에 얹었을 뿐 앞의 둘을 지우지 않는다.
 */
public record AnalyticsKpiResponse(
		OffsetDateTime from,
		OffsetDateTime to,
		List<EventTypeCountEntry> eventCounts,
		OutboxHealthEntry outboxHealth,
		RecommendationJobHealthEntry recommendationJobHealth) {

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

	/**
	 * 추천 요청(Job)의 성공률·처리 시간·실패 사유 — S15P21E201-160 · S15P21E201-969 논의 이후.
	 *
	 * <p>🔴 {@code createdAt}({@code from}~{@code to}) 기준으로 <b>그 기간에 접수된 Job</b>을
	 * 본다. {@code PENDING}·{@code RUNNING}·{@code CANCELLED}·{@code EXPIRED} 도
	 * {@link #statusCounts()} 에는 그대로 잡히지만, {@link #successRatePercent()} 의 분모에는
	 * {@code SUCCEEDED}·{@code FAILED} 둘만 넣는다 — 아직 안 끝난 요청을 실패로도 성공으로도
	 * 접지 않기 위해서다.
	 *
	 * @param statusCounts 그 기간에 접수된 Job 을 상태별로 센 것. 진행 중인 것도 그대로 들어간다
	 * @param successRatePercent {@code SUCCEEDED / (SUCCEEDED + FAILED) * 100}. 그 기간에 끝난
	 *     Job 이 하나도 없으면(전부 진행 중이거나 접수 자체가 없으면) {@code null} — 0 이나
	 *     100 으로 답하면 "쟀는데 그 값이었다" 와 "잴 것이 없었다" 가 구분되지 않는다
	 * @param averageLatencyMsForSucceeded 성공한 Job 의 평균 처리 시간. 성공한 Job 이 없으면
	 *     {@code null}
	 * @param failureBreakdown 실패한 Job 을 {@code errorCode} 별로 센 것. 실패가 없으면 빈 목록
	 */
	public record RecommendationJobHealthEntry(
			List<JobStatusCountEntry> statusCounts,
			Double successRatePercent,
			Double averageLatencyMsForSucceeded,
			List<ErrorCodeCountEntry> failureBreakdown) {
	}

	public record JobStatusCountEntry(String status, long count) {
	}

	public record ErrorCodeCountEntry(String errorCode, long count) {
	}
}
