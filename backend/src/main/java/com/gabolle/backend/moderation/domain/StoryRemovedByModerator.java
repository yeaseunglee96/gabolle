package com.gabolle.backend.moderation.domain;

import java.util.UUID;

/**
 * 운영자가 신고된 기록을 삭제로 처리했다 — S15P21E201-794.
 *
 * <p>이 사건을 띄우는 쪽({@code ModerationQueueService.remove})은 <b>누가 어떻게 알리는지 모른다.</b>
 * 듣는 쪽이 없어도 삭제는 그대로 끝난다. 그렇게 둔 이유는 그 클래스의 {@code publishRemoved}
 * javadoc 에 적어 두었다 — 기록·신고 통합 테스트가 띄우는 슬라이스에 메일 발송기가 없다.
 *
 * <p>🔴 기각(dismiss)에는 이 사건이 없다. 기각은 사용자 눈에 아무 일도 없었던 것이 맞아서
 * 알리지 않는다(티켓 완료 기준).
 *
 * <p>🔴 <b>신고 사유와 신고자를 담지 않는다.</b> 담으면 듣는 쪽이 그것을 쓸 수 있는 값으로 보고
 * 언젠가 알림에 싣는다. 사용자 수가 적은 서비스에서는 사유 한 가지만으로도 누가 신고했는지가
 * 좁혀지고, 그 추측이 맞든 틀리든 사람 사이의 일이 된다. 알릴 것을 못 담게 해 두는 것이 문서로
 * 적어 두는 것보다 강하다.
 *
 * @param authorUserId 알림을 받아야 하는 사람. 그 사람에게 닿는 주소가 <b>없을 수도 있다</b> —
 * 애플과 기본 동의 카카오는 이메일을 주지 않는다
 * @param excerpt 어느 기록인지 알아볼 만큼의 본문 앞부분. 띄우는 쪽이 이미 잘라서 담는다
 */
public record StoryRemovedByModerator(UUID storyId, UUID authorUserId, String excerpt) {
}
