package com.gabolle.backend.story.presentation.dto;

/**
 * 팔로워·팔로잉·차단 목록의 사람 한 명.
 *
 * @param following 지금 보는 사람(로그인한 사람)이 이 사람을 팔로우하는가 — S15P21E201-1179.
 *                  목록을 소유한 사람(팔로워·팔로잉 목록의 주인) 기준이 아니라 <b>보는 사람</b>
 *                  기준이다. 화면의 팔로우 버튼이 이 값으로 초기 상태를 그린다.
 * @param storyCount 이 사람이 쓴 기록 중 <b>보는 사람에게 보이는</b> 것의 수 — S15P21E201-1317.
 *                  이름만 있는 목록은 그냥 글자 줄이라, 누구를 다시 볼지 고르려면 이 숫자가 있어야
 *                  한다.
 *                  <p>🔴 <b>{@code null} 은 0 이 아니라 「안 셌다」다.</b> 차단 목록은 이 숫자를
 *                  안 그리므로 세지 않고 {@code null} 로 보낸다. 안 센 것을 0 으로 채우면
 *                  화면이 「기록 0개」라고 <b>단언</b>하게 되는데, 그건 사실이 아니다.
 */
public record RelationItemResponse(String userId, String displayName, String avatarUrl, boolean following,
		Long storyCount) {
}
