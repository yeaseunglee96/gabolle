package com.gabolle.backend.trip.domain;

import java.time.Instant;

/**
 * 여행 참여자 — ERD 의 {@code TRIP_MEMBERS} 표.
 *
 * <p>🔴 <b>여행을 만든 사람도 여기 들어가야 한다.</b> 안 들어가면 자기 여행을 못 본다 —
 * 조회 권한 판정이 이 표를 보기 때문이다(API 명세 2.1 · FR-SEC-01).
 *
 * <p>그래서 {@link #owner} 를 여행 생성과 <b>같은 트랜잭션</b>에서 만든다.
 * 별도 호출로 두면 언젠가 빠뜨린다.
 */
public class TripMember {

    private final String tripMemberId;
    private final String tripId;
    private final String userId;
    private final Role role;
    private final Instant joinedAt;

    /**
     * 초대 흔적 — S15P21E201-299. 소유자 행은 셋 다 {@code null} 이다. "누가·어떤 역할로·언제
     * 초대됐고 언제 들어왔는지" 가 완료 기준이라 참여자 행에 그대로 남긴다.
     */
    private final String tripInviteId;
    private final String invitedBy;
    private final Instant invitedAt;

    private TripMember(String tripMemberId, String tripId, String userId, Role role, Instant joinedAt,
                       String tripInviteId, String invitedBy, Instant invitedAt) {
        this.tripMemberId = tripMemberId;
        this.tripId = tripId;
        this.userId = userId;
        this.role = role;
        this.joinedAt = joinedAt;
        this.tripInviteId = tripInviteId;
        this.invitedBy = invitedBy;
        this.invitedAt = invitedAt;
    }

    /** 여행을 만든 사람. 생성과 동시에 들어간다. */
    public static TripMember owner(String id, String tripId, String userId, Instant at) {
        return new TripMember(id, tripId, userId, Role.OWNER, at, null, null, null);
    }

    /** 초대받아 들어온 사람 (GRP-01). 초대 흔적이 없는 옛 행(2026-09-07 이전)을 되살릴 때도 쓴다. */
    public static TripMember invited(String id, String tripId, String userId, Role role, Instant at) {
        return invited(id, tripId, userId, role, at, null, null, null);
    }

    /**
     * 초대 표로 들어온 사람 — S15P21E201-299. 어느 초대(`tripInviteId`)로, 누가(`invitedBy`),
     * 언제(`invitedAt`) 초대했는지를 함께 남긴다.
     */
    public static TripMember invited(String id, String tripId, String userId, Role role, Instant at,
                                     String tripInviteId, String invitedBy, Instant invitedAt) {
        if (role == Role.OWNER) {
            // 🔴 소유자는 초대로 만들어지지 않는다. 여행마다 하나이고 생성 시 정해진다.
            throw new IllegalArgumentException("OWNER 는 초대로 만들 수 없다");
        }
        return new TripMember(id, tripId, userId, role, at, tripInviteId, invitedBy, invitedAt);
    }

    /**
     * S15P21E201-317 — 익명 세션 소유의 여행을 승계할 때, OWNER 행의 주인을 새 회원으로 옮긴다.
     *
     * <p>🔴 OWNER 행에만 쓴다. 익명 세션으로 만든 여행에는 그 세션을 가리키는 참여자 행이
     * OWNER 하나뿐이다 — 초대는 회원만 받을 수 있어 익명 여행에는 EDITOR·VIEWER 가 없다.
     */
    public TripMember claimedBy(String newUserId) {
        if (this.role != Role.OWNER) {
            throw new IllegalArgumentException("소유자 행만 승계할 수 있다");
        }
        return new TripMember(tripMemberId, tripId, newUserId, role, joinedAt, tripInviteId, invitedBy, invitedAt);
    }

    /**
     * 역할을 바꾼 새 값 — S15P21E201-320. 소유자만 부를 수 있다는 판정은 서비스가 한다. 여기서는
     * OWNER 로 바꾸거나 OWNER 를 바꾸는 것만 막는다 — 소유자는 여행마다 하나이고 생성 시 정해진다.
     */
    public TripMember withRole(Role newRole) {
        if (this.role == Role.OWNER || newRole == Role.OWNER) {
            throw new IllegalArgumentException("소유자 역할은 바꿀 수 없다");
        }
        return new TripMember(tripMemberId, tripId, userId, newRole, joinedAt, tripInviteId, invitedBy, invitedAt);
    }

    /**
     * 역할.
     *
     * <p>🔴 v1.1 확정 사항 — "초대 구성원 전원 EDITOR". VIEWER 는 읽기 전용 공유 링크용이다.
     */
    public enum Role {
        /** 여행을 만든 사람. 삭제·역할 변경 가능 */
        OWNER,
        /** 같이 고칠 수 있다 */
        EDITOR,
        /** 보기만 한다 */
        VIEWER;

        public boolean canEdit() {
            return this == OWNER || this == EDITOR;
        }
    }

    public String tripMemberId() { return tripMemberId; }
    public String tripId()       { return tripId; }
    public String userId()       { return userId; }
    public Role role()           { return role; }
    public Instant joinedAt()    { return joinedAt; }
    /** 초대 표. 소유자이거나 초대 흔적이 없는 옛 행이면 {@code null}. */
    public String tripInviteId() { return tripInviteId; }
    public String invitedBy()    { return invitedBy; }
    public Instant invitedAt()   { return invitedAt; }
}
