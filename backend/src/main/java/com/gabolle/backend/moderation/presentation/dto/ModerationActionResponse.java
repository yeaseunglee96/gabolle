package com.gabolle.backend.moderation.presentation.dto;

import java.util.UUID;

/**
 * 삭제·기각 처리 결과 — S15P21E201-267.
 *
 * @param storyId 처리한 기록
 * @param resolvedReportCount 이번에 함께 처리한 미처리 신고 수. 하나만 처리하고 나머지를 큐에 남기지
 *        않았다는 확인이다
 */
public record ModerationActionResponse(UUID storyId, int resolvedReportCount) {
}
