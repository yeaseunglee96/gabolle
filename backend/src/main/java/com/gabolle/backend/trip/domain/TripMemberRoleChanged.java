package com.gabolle.backend.trip.domain;

/**
 * 소유자가 동행자의 자격을 바꿨다 — S15P21E201-1391.
 *
 * <p>🔴 <b>이 알림만 받는 사람이 하나다.</b> 나머지 셋은 「여행의 다른 사람 전부」에게 가지만,
 * 자격이 바뀐 것은 <b>당사자에게만</b> 쓸모가 있다 — 방금까지 되던 편집이 안 되는 이유를
 * 그 사람만 알아야 한다. 남들에게는 「누가 누구를 강등했다」는 소식일 뿐이다.
 *
 * @param tripId 어느 여행
 * @param targetUserId 자격이 바뀐 사람. <b>받는 사람이 이 사람이다</b>
 * @param newRole 바뀐 자격 (EDITOR · VIEWER)
 * @param actorUserId 바꾼 소유자. 자기가 자기 자격을 바꿀 수는 없으므로 늘 다른 사람이지만,
 *     받는 쪽이 그 가정에 기대지 않도록 담아 둔다
 */
public record TripMemberRoleChanged(String tripId, String targetUserId, TripMember.Role newRole,
		String actorUserId) {
}
