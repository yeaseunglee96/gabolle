package com.gabolle.backend.itinerary.infra;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * {@code user_pace_factor} 표 매핑.
 * 판을 새로 여는 자리({@link #open})와 앞 판에 자리를 내주는 자리({@link #supersede})만 있고
 * 그 밖의 칸은 만든 뒤 바꾸지 않는다({@code updatable = false}) — 이 표의 행은 판이 통째로
 * 바뀌는 것이지 값 하나씩 고치는 대상이 아니다.
 */
@Entity
@Table(name = "user_pace_factor")
public class PaceFactorJpaEntity {

    @Id
    @Column(name = "user_pace_factor_id", nullable = false, updatable = false)
    private UUID userPaceFactorId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "version", nullable = false, updatable = false)
    private int version;

    @Column(name = "factor", nullable = false, updatable = false)
    private BigDecimal factor;

    @Column(name = "sample_count", nullable = false, updatable = false)
    private int sampleCount;

    @Column(name = "observed_until", nullable = false, updatable = false)
    private OffsetDateTime observedUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** 비어 있으면 현재 판이다. 다음 판이 나오면 찍힌다 — 행을 지우지 않는다. */
    @Column(name = "superseded_at")
    private OffsetDateTime supersededAt;

    protected PaceFactorJpaEntity() {
        // JPA 전용
    }

    private PaceFactorJpaEntity(UUID userPaceFactorId, UUID userId, int version, BigDecimal factor,
            int sampleCount, OffsetDateTime observedUntil, OffsetDateTime createdAt) {
        this.userPaceFactorId = userPaceFactorId;
        this.userId = userId;
        this.version = version;
        this.factor = factor;
        this.sampleCount = sampleCount;
        this.observedUntil = observedUntil;
        this.createdAt = createdAt;
    }

    /**
     * 새 판을 연다.
     * 이 메서드는 이 판을 현재로 만들지 않는다. 앞선 판에 {@link #supersede} 를 찍는 것과 같은
     * 트랜잭션 안에서 불려야 하고, 그 순서를 지키는 것은 {@code JpaPaceFactorRepository} 의 몫이다.
     */
    static PaceFactorJpaEntity open(UUID userId, int version, BigDecimal factor, int sampleCount,
            OffsetDateTime observedUntil, OffsetDateTime createdAt) {
        return new PaceFactorJpaEntity(UUID.randomUUID(), userId, version, factor, sampleCount, observedUntil,
                createdAt);
    }

    /** 다음 판에 자리를 내준다. 행은 남는다 — 과거 계산을 설명하려면 필요하다. */
    void supersede(OffsetDateTime at) {
        this.supersededAt = at;
    }

    UUID userPaceFactorId() { return userPaceFactorId; }
    UUID userId() { return userId; }
    int version() { return version; }
    BigDecimal factor() { return factor; }
    int sampleCount() { return sampleCount; }
    OffsetDateTime observedUntil() { return observedUntil; }
    OffsetDateTime createdAt() { return createdAt; }
    OffsetDateTime supersededAt() { return supersededAt; }
}
