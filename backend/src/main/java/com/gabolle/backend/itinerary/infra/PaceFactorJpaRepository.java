package com.gabolle.backend.itinerary.infra;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * {@code user_pace_factor} 판 조회. 현재 판을 찾는 메서드 하나만 있다 — 이 표를 판마다 훑는
 * 조회는 아직 없고, 생기면 그때 더한다.
 */
interface PaceFactorJpaRepository extends JpaRepository<PaceFactorJpaEntity, UUID> {

    /**
     * 지금 쓰는 판. 한 사람에게 최대 하나임을 표의 조건부 UNIQUE 색인이 보장한다.
     */
    Optional<PaceFactorJpaEntity> findByUserIdAndSupersededAtIsNull(UUID userId);
}
