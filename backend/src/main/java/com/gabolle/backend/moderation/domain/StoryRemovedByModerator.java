package com.gabolle.backend.moderation.domain;

import java.util.UUID;

/**
 * 운영자가 신고된 기록을 삭제로 처리했다. 듣는 쪽이 없어도 삭제는 그대로 끝난다. 기각에는 이
 * 사건이 없다 — 기각은 사용자 눈에 아무 일도 없었던 것이 맞다.
 *
 * 신고 사유와 신고자를 일부러 담지 않는다. 담아 두면 언젠가 알림에 실리고, 사용자가 적은
 * 서비스에서는 사유 하나만으로도 누가 신고했는지가 좁혀진다.
 *
 * @param authorUserId 알림 대상. 애플과 기본 동의 카카오는 이메일을 주지 않아 닿을 주소가 없을 수 있다
 * @param excerpt 본문 앞부분. 띄우는 쪽이 이미 잘라서 담는다
 */
public record StoryRemovedByModerator(UUID storyId, UUID authorUserId, String excerpt) {
}
