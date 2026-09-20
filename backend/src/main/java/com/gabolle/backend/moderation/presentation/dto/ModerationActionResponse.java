package com.gabolle.backend.moderation.presentation.dto;

import java.util.UUID;

/**
 * @param resolvedReportCount 이번에 함께 닫은 미처리 신고 수 — 나머지를 큐에 남기지 않았다는 확인이다
 */
public record ModerationActionResponse(UUID storyId, int resolvedReportCount) {
}
