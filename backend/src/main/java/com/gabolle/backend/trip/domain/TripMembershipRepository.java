package com.gabolle.backend.trip.domain;

import java.util.Optional;

/**
 * 참여자 행의 변경 — S15P21E201-299(수락) · -320(역할 변경·제거).
 *
 * <p>{@link TripRepository} 에 넣지 않은 이유 — 그쪽은 여행 생성·조회의 계약이고 인메모리 구현까지
 * 갖고 있다. 참여자 변경은 협업 기능이 생기면서 처음 필요해진 것이라 계약을 따로 두고, 구현은 DB 만
 * 둔다(협업 서비스가 전부 {@code @Profile({"db","dev"})} 라 인메모리 구현이 필요한 자리가 없다).
 *
 * <p>🔴 "이미 참여 중" 판정을 여기 {@link #findMember} 로 먼저 하고 {@link #add} 를 부르는 두 단계로
 * 두면, 같은 표를 동시에 두 번 누를 때 둘 다 "없음" 을 보고 둘 다 넣으려 한다. 그때 마지막
 * 방어선은 {@code uq_trip_member (trip_id, user_id)} 다 — 진 쪽은 예외를 받고, 서비스는 그것을
 * "이미 참여했다"(성공) 로 번역한다. UNIQUE 를 지우면 안 되는 이유가 이것이다.
 */
public interface TripMembershipRepository {

    Optional<TripMember> findMember(String tripId, String userId);

    /**
     * 참여자 한 명을 더한다.
     *
     * @throws AlreadyMemberException 같은 (tripId, userId) 가 이미 있다 — UNIQUE 가 잡은 경쟁
     */
    TripMember add(TripMember member);

    /** 역할을 바꾼다. 행이 없으면 아무 일도 하지 않고 비어 있다. */
    Optional<TripMember> changeRole(String tripId, String userId, TripMember.Role newRole);

    /** 참여자를 뺀다. 지운 행 수를 돌려준다(0 이면 원래 없었다). */
    int remove(String tripId, String userId);

    /** 같은 사람이 같은 여행에 두 번 들어오려 했다 — UNIQUE 가 막았다. 서비스는 이것을 성공(이미 참여)으로 번역한다. */
    class AlreadyMemberException extends RuntimeException {
        public AlreadyMemberException(String tripId, String userId) {
            super("이미 참여 중이다: tripId=" + tripId + ", userId=" + userId);
        }
    }
}
