package com.gabolle.backend.trip.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * 동행자 초대 표 한 장. 링크는 메신저와 단체 대화방에 남으므로 발급 시점부터 {@link #TTL} 뒤에
 * 만료된다.
 *
 * <p>만료는 행을 지우는 것이 아니라 {@code expiresAt} 을 지나는 것이다. 지난 표는 410 으로
 * 답한다 — 행을 지우면 "없다"(404)와 구분이 안 된다. {@code token} 은 이 표의 유일한 잠금이라
 * 순번이나 시각에서 파생하지 않고 32바이트 난수로 만든다.
 */
public class TripInvite {

    public static final Duration TTL = Duration.ofDays(7);

    private final String tripInviteId;
    private final String tripId;
    private final String token;
    private final TripMember.Role role;
    private final String createdBy;
    private final Instant createdAt;
    private final Instant expiresAt;

    public TripInvite(String tripInviteId, String tripId, String token, TripMember.Role role,
                      String createdBy, Instant createdAt, Instant expiresAt) {
        if (role == TripMember.Role.OWNER) {
            throw new IllegalArgumentException("OWNER 역할로는 초대할 수 없다");
        }
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("초대 표(token)는 비울 수 없다");
        }
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("만료 시각은 발급 시각보다 뒤여야 한다");
        }
        this.tripInviteId = tripInviteId;
        this.tripId = tripId;
        this.token = token;
        this.role = role;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public static TripInvite issue(String id, String tripId, String token, TripMember.Role role,
                                   String createdBy, Instant now) {
        return new TripInvite(id, tripId, token, role, createdBy, now, now.plus(TTL));
    }

    public boolean isExpiredAt(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public String tripInviteId() { return tripInviteId; }
    public String tripId()       { return tripId; }
    public String token()        { return token; }
    public TripMember.Role role() { return role; }
    public String createdBy()    { return createdBy; }
    public Instant createdAt()   { return createdAt; }
    public Instant expiresAt()   { return expiresAt; }
}
