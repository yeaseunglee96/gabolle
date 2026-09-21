package com.gabolle.backend.moderation.presentation.dto;

import java.util.List;

/** 미처리 신고가 있는 기록을 오래된 순으로 담는다. */
public record ModerationQueueResponse(List<ModerationQueueItemResponse> items) {
}
