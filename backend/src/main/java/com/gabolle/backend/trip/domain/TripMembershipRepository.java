package com.gabolle.backend.trip.domain;

import java.util.Optional;

/**
 * 참여자 행의 변경. 구현은 DB 만 둔다 — 협업 서비스가 전부 {@code @Profile({"db","dev"})} 다.
 *
 * <p>{@link #findMember} 로 "이미 참여 중" 을 먼저 보고 {@link #add} 를 부르면 같은 표를 동시에
 * 두 번 누를 때 둘 다 "없음" 을 본다. 마지막 방어선은 {@code uq_trip_member (trip_id, user_id)} 이고,
 * 서비스는 진 쪽의 예외를 "이미 참여했다"(성공)로 번역한다.
 */
public interface TripMembershipRepository {

    Optional<TripMember> findMember(String tripId, String userId);

    /** @throws AlreadyMemberException 같은 (tripId, userId) 가 이미 있다 — UNIQUE 가 잡은 경쟁 */
    TripMember add(TripMember member);

    /** 역할을 바꾼다. 행이 없으면 아무 일도 하지 않고 비어 있다. */
    Optional<TripMember> changeRole(String tripId, String userId, TripMember.Role newRole);

    /** 참여자를 뺀다. 지운 행 수를 돌려준다(0 이면 원래 없었다). */
    int remove(String tripId, String userId);

    class AlreadyMemberException extends RuntimeException {
        public AlreadyMemberException(String tripId, String userId) {
            super("이미 참여 중이다: tripId=" + tripId + ", userId=" + userId);
        }
    }
}
