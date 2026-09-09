package com.gabolle.backend.moderation.presentation.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 검토 목록 한 줄 — 신고당한 기록 하나(신고가 여럿이어도 하나로 묶는다). S15P21E201-267.
 *
 * <p>🔴 신고자를 담지 않는다. 운영자가 판단할 것은 기록의 내용이고 누가 신고했는지가 판단을
 * 바꾸면 안 된다({@code StoryReport} 클래스 주석 참고).
 *
 * @param storyId 기록 식별자
 * @param authorName 작성자 표시 이름. 탈퇴 등으로 사용자 행이 없으면 {@code null}
 * @param body 기록 본문(운영자가 읽고 판단할 내용)
 * @param reasons 이 기록에 걸린 미처리 신고 사유들. 가장 오래된 신고의 사유가 먼저 온다. 중복은 없다
 * @param reportCount 미처리 신고 수(같은 사람의 중복 신고는 표의 UNIQUE 가 이미 걸러 여기 안 잡힌다)
 * @param oldestReportedAt 가장 오래된 미처리 신고가 접수된 시각
 * @param elapsedSeconds {@code oldestReportedAt} 부터 지금까지 지난 초. 서버가 계산해 화면이 시계를
 *        안 봐도 되게 한다
 */
public record ModerationQueueItemResponse(UUID storyId, String authorName, String body, List<String> reasons,
		int reportCount, Instant oldestReportedAt, long elapsedSeconds) {
}
