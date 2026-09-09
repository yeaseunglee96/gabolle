package com.gabolle.backend.moderation.presentation.dto;

import java.util.List;

/** 검토 목록 — 미처리 신고가 있는 기록을 오래된 순으로. S15P21E201-267. */
public record ModerationQueueResponse(List<ModerationQueueItemResponse> items) {
}
