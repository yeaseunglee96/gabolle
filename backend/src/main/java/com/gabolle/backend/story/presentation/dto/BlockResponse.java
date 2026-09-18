package com.gabolle.backend.story.presentation.dto;

/**
 * 차단·해제의 결과 — S15P21E201-990.
 *
 * <p>🔴 팔로워 수를 돌려주지 않는다. {@link FollowResponse} 는 팔로우가 바뀌면 그 수가 화면에
 * 곧바로 나오므로 같이 실어 보내지만, 차단은 <b>상대의 프로필을 떠나는 동작</b>이라 그 수를 그릴
 * 자리가 없다. 필요해지면 그때 더한다 — 지금 실어 보내면 아무도 안 읽는 값을 매번 세게 된다.
 *
 * @param userId  대상 사용자
 * @param blocked 이제 차단된 상태인가
 */
public record BlockResponse(String userId, boolean blocked) {
}
