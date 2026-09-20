package com.gabolle.backend.itinerary.infra;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.itinerary.domain.PaceFactor;
import com.gabolle.backend.itinerary.domain.PaceFactorRepository;

/**
 * 개인 속도 계수 저장소.
 * {@link #appendVersion} 을 한 트랜잭션으로 묶는다. 앞 판을 지우지 않고 {@code superseded_at}
 * 만 찍은 뒤 새 판을 넣는데, 이 둘이 갈라지면 그 사이에 "지금 판" 이 없거나 둘이 되는 순간이
 * 생기고 조건부 UNIQUE 색인({@code uq_user_pace_factor_current})이 둘 중 하나를 거부한다.
 * 원시 {@code ON CONFLICT} 문장을 쓰지 않는다. 방문 실제 시각 쪽은 같은 방문지에 두 번 보내는
 * 것이 정상 경로라 충돌을 흡수해야 했지만, 같은 사용자에게 새 판을 여는 것은 동시에 두 번
 * 일어날 일이 드물어 일반 JPA 조회·저장으로 충분하다.
 */
@Repository
@Profile({ "db", "dev" })
public class JpaPaceFactorRepository implements PaceFactorRepository {

    private final PaceFactorJpaRepository jpaRepository;

    public JpaPaceFactorRepository(PaceFactorJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PaceFactor> findCurrent(String userId) {
        return this.jpaRepository.findByUserIdAndSupersededAtIsNull(UUID.fromString(userId))
                .map(JpaPaceFactorRepository::toDomain);
    }

    @Override
    @Transactional
    public PaceFactor appendVersion(String userId, BigDecimal factor, int sampleCount, Instant observedUntil,
            Instant now) {
        UUID userUuid = UUID.fromString(userId);
        OffsetDateTime nowOffset = toOffset(now);

        Optional<PaceFactorJpaEntity> current = this.jpaRepository.findByUserIdAndSupersededAtIsNull(userUuid);
        // version 채번 — 앞 판이 있으면 그 version + 1, 없으면 1. UUID 는 순서가 없어서 이 정수가
        // 몇 번째 판인지를 대신 답한다.
        int nextVersion = current.map(e -> e.version() + 1).orElse(1);

        // 앞 판의 supersede 를 여기서 먼저 내보낸다. 값만 바꿔 두고 아래 save 로 넘어가면 표가
        // 새 판을 거부한다 — Hibernate 는 한 번의 flush 안에서 INSERT 를 UPDATE 보다 먼저 실행해서,
        // 새 행이 들어가는 순간 앞 행의 superseded_at 이 아직 비어 있고 조건부 UNIQUE 색인이 보기에
        // "지금 판" 이 둘이 된다.
        // 트랜잭션이 하나라는 것만으로는 안 막힌다 — 트랜잭션은 두 문장이 함께 커밋되는 것을
        // 보장할 뿐 그 안에서의 순서를 정해 주지 않는다.
        current.ifPresent(e -> {
            e.supersede(nowOffset);
            this.jpaRepository.saveAndFlush(e);
        });

        PaceFactorJpaEntity next = PaceFactorJpaEntity.open(userUuid, nextVersion, factor, sampleCount,
                toOffset(observedUntil), nowOffset);
        PaceFactorJpaEntity saved = this.jpaRepository.save(next);

        return toDomain(saved);
    }

    private static PaceFactor toDomain(PaceFactorJpaEntity e) {
        return new PaceFactor(
                e.userPaceFactorId().toString(),
                e.userId().toString(),
                e.version(),
                e.factor(),
                e.sampleCount(),
                toInstant(e.observedUntil()),
                toInstant(e.createdAt()),
                toInstant(e.supersededAt()));
    }

    private static OffsetDateTime toOffset(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    private static Instant toInstant(OffsetDateTime offsetDateTime) {
        return offsetDateTime == null ? null : offsetDateTime.toInstant();
    }
}
