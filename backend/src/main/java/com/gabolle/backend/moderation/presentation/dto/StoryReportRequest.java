package com.gabolle.backend.moderation.presentation.dto;

/**
 * 신고 접수 요청 — S15P21E201-254.
 *
 * <p>{@code reason} 은 {@code PRIVACY}·{@code OFFENSIVE}·{@code SPAM}·{@code OTHER} 중 하나의 문자열이다.
 * 모르는 값·빈 값은 {@code StoryReportService} 가 400 으로 거절한다 — 여기서는 형식 검증만 하지 않는다
 * (Bean Validation 을 걸지 않은 이유는 "모르는 사유" 판정 자체가 {@link com.gabolle.backend.moderation.domain.StoryReportReason#from}
 * 하나로 이미 끝나서 애너테이션을 더할 실익이 없기 때문이다).
 *
 * @param reason 신고 사유
 * @param detail {@code OTHER} 일 때만 쓰는 자유 입력. 그 밖의 사유면 서버가 버린다
 */
public record StoryReportRequest(String reason, String detail) {
}
