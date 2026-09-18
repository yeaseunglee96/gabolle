package com.gabolle.backend.trip.infra;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 여행 조건 답 — S15P21E201-1231.
 *
 * <p>찾는 메서드를 따로 안 둔다. 기본키가 {@code user_id} 라 {@code findById(userId)} 가
 * 곧 「이 사람의 답」이다.
 */
public interface TravelConstraintJpaRepository extends JpaRepository<TravelConstraintJpaEntity, UUID> {
}
