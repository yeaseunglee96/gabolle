package com.gabolle.backend.moderation.presentation.dto;

/**
 * @param reason {@code PRIVACY}·{@code OFFENSIVE}·{@code SPAM}·{@code OTHER} 중 하나. 모르는 값과 빈
 *        값은 {@code StoryReportService} 가 400 으로 거절한다(Bean Validation 을 걸지 않았다)
 * @param detail {@code OTHER} 일 때만 쓰는 자유 입력. 그 밖의 사유면 서버가 버린다
 */
public record StoryReportRequest(String reason, String detail) {
}
