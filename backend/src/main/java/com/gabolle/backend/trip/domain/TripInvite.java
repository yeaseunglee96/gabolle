package com.gabolle.backend.trip.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * 동행자 초대 표 한 장 — S15P21E201-294 (F-COL-01).
 *
 * <p>초대 링크는 메신저와 단체 대화방에 남는다. 기한이 없으면 몇 달 뒤 그 대화방을 뒤진 사람이
 * 남의 여행에 들어온다. 그래서 만드는 시점부터 {@link #TTL 7일} 뒤에 만료된다.
 *
 * <p>🔴 만료는 행을 지우는 것이 아니라 {@code expiresAt} 을 지나는 것이다. 지난 표로 들어오면
 * "만료됐다"(410)로 답한다 — 행을 지우면 "없다"(404)와 구분이 안 되고, 화면이 만료 안내와 잘못된
 * 주소를 다르게 그릴 수 없다.
 *
 * <p>{@code token} 은 URL 에 실리는 값이고 이 표의 유일한 잠금이다. 만드는 쪽
 * ({@code TripInviteService})이 32바이트 난수를 base64url 로 적는다 — 순번이나 시각에서 파생한
 * 값을 넣지 않는다.
 */
public class TripInvite {

    /** 초대 표의 수명. 티켓이 "7일" 로 못 박았다. */
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
            // 🔴 소유자는 초대로 만들어지지 않는다 — TripMember.invited 와 같은 규칙이다.
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

    /** 지금 발급하는 초대. 만료는 발급 시각 + {@link #TTL}. */
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
