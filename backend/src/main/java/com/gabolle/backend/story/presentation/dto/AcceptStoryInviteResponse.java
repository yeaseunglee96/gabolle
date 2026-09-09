package com.gabolle.backend.story.presentation.dto;

/**
 * 기록 공동 작성 초대 수락 응답 — S15P21E201-770.
 *
 * <p>{@code alreadyJoined} 가 {@code true} 면 이번 호출로 새로 합류한 것이 아니다 — 이미 참여
 * 중이던 공동 작성자거나, 만든 사람이 자기 링크를 눌렀거나, 같은 표를 동시에 두 번 눌러 진 쪽이다.
 * 셋 다 실패가 아니라 성공으로 답한다 — 링크를 두 번 눌렀다고 사용자에게 오류 화면을 보여줄
 * 이유가 없다({@code AcceptInviteResponse.alreadyMember} 와 같은 이유).
 *
 * <p>{@code joinedAt} 은 만든 사람이 자기 링크를 눌렀을 때는 {@code null} 이다 — 만든 사람은
 * {@code story_coauthor} 행이 없어서(그 표의 뜻 자체가 "만든 사람은 들어가지 않는다") 합류 시각이
 * 존재하지 않는다.
 */
public record AcceptStoryInviteResponse(String storyId, boolean alreadyJoined, String joinedAt) {
}
