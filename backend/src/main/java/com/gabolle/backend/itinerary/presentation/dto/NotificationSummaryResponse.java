package com.gabolle.backend.itinerary.presentation.dto;

/**
 * 종 점 — 내 여행들에 새 활동이 있나 (S15P21E201-1699). {@code GET /api/v1/me/notification-summary}.
 *
 * @param hasUnseen {@code since} 가 없으면 활동이 하나라도 있는지, 있으면 가장 최근 활동이 그보다 나중인지
 * @param latestAt 내 모든 여행 활동 가운데 가장 최근 시각(ISO-8601). 여행 활동 조회의 {@code entry.at} 과 같은 값이다.
 *     활동이 없으면 {@code null}
 */
public record NotificationSummaryResponse(boolean hasUnseen, String latestAt) {
}
