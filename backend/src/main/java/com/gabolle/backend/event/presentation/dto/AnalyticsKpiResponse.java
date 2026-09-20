package com.gabolle.backend.event.presentation.dto;

import java.time.OffsetDateTime;
import java.util.List;

/** {@code GET /api/v1/admin/analytics/kpis} 응답. */
public record AnalyticsKpiResponse(
		OffsetDateTime from,
		OffsetDateTime to,
		List<EventTypeCountEntry> eventCounts,
		OutboxHealthEntry outboxHealth,
		RecommendationJobHealthEntry recommendationJobHealth) {

	public record EventTypeCountEntry(String eventType, long count) {
	}

	/**
	 * @param pendingCount 아직 안 보낸 건수. {@code from}~{@code to} 와 무관한 실시간 값이라
	 *     기간을 좁혀도 안 바뀐다
	 * @param oldestPendingAgeSeconds 제일 오래 밀린 건이 들어온 지 몇 초 됐는가. 밀린 게 없으면
	 *     {@code null} — 0 을 쓰면 "방금 밀리기 시작했다" 와 구분이 안 된다
	 * @param publishedCount {@code from}~{@code to} 사이에 실제로 나간 건수
	 * @param failedCount 재시도해도 계속 실패 중인 건수. 실시간 값이다
	 */
	public record OutboxHealthEntry(
			long pendingCount,
			Long oldestPendingAgeSeconds,
			long publishedCount,
			long failedCount) {
	}

	/**
	 * 추천 요청의 성공률·처리 시간·실패 사유. {@code createdAt} 이 {@code from}~{@code to} 안인
	 * Job 을 본다.
	 *
	 * @param statusCounts 상태별 건수. 아직 안 끝난 것도 그대로 들어간다
	 * @param successRatePercent {@code SUCCEEDED / (SUCCEEDED + FAILED) * 100}. 분모에 끝난 것만
	 *     넣어 진행 중인 요청을 성공으로도 실패로도 접지 않는다. 끝난 Job 이 없으면 {@code null} —
	 *     0 이나 100 으로 답하면 "쟀는데 그 값" 과 "잴 것이 없었다" 가 구분되지 않는다
	 * @param averageLatencyMsForSucceeded 성공한 Job 이 없으면 {@code null}
	 * @param failureBreakdown 실패가 없으면 빈 목록
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
