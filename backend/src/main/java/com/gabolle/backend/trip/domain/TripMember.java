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

    private TripMember(String tripMemberId, String tripId, String userId, Role role, Instant joinedAt) {
        this.tripMemberId = tripMemberId;
        this.tripId = tripId;
        this.userId = userId;
        this.role = role;
        this.joinedAt = joinedAt;
    }

    /** 여행을 만든 사람. 생성과 동시에 들어간다. */
    public static TripMember owner(String id, String tripId, String userId, Instant at) {
        return new TripMember(id, tripId, userId, Role.OWNER, at);
    }

    /** 초대받아 들어온 사람 (GRP-01). */
    public static TripMember invited(String id, String tripId, String userId, Role role, Instant at) {
        if (role == Role.OWNER) {
            // 🔴 소유자는 초대로 만들어지지 않는다. 여행마다 하나이고 생성 시 정해진다.
            throw new IllegalArgumentException("OWNER 는 초대로 만들 수 없다");
        }
        return new TripMember(id, tripId, userId, role, at);
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
}
