package com.gabolle.backend.moderation.presentation.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 검토 목록 한 줄 — 신고가 여럿이어도 기록 하나로 묶는다. 신고자는 담지 않는다(누가 신고했는지가
 * 운영자의 판단을 바꾸면 안 된다).
 *
 * @param authorName 탈퇴 등으로 사용자 행이 없으면 {@code null}
 * @param reasons 미처리 신고 사유들. 가장 오래된 신고의 사유가 먼저 오고 중복은 없다
 * @param reportCount 미처리 신고 수. 같은 사람의 중복 신고는 표의 UNIQUE 가 이미 걸렀다
 * @param elapsedSeconds {@code oldestReportedAt} 부터 지금까지 지난 초. 화면이 시계를 안 보게 서버가 센다
 */
public record ModerationQueueItemResponse(UUID storyId, String authorName, String body, List<String> reasons,
		int reportCount, Instant oldestReportedAt, long elapsedSeconds) {
}
