package com.gabolle.backend.trip.domain;

/**
 * 초대를 수락해 새 동행자가 들어왔다 — S15P21E201-1391.
 *
 * <p>이미 참여 중인 사람이 표를 한 번 더 누른 경우에는 <b>내지 않는다.</b> 그것은 아무 일도
 * 일어나지 않은 것이고, 같은 알림이 두 번 뜨면 사람은 두 사람이 들어온 줄 안다.
 *
 * @param tripId 어느 여행
 * @param joinedUserId 들어온 사람. <b>이 사람에게는 안 보낸다</b> — 자기가 방금 누른 것이다
 * @param role 들어온 자격 (EDITOR · VIEWER)
 */
public record TripMemberJoined(String tripId, String joinedUserId, TripMember.Role role) {
}
