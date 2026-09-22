package com.gabolle.backend.event.repository;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gabolle.backend.event.domain.EventConsumption;

/**
 * 반영 장부 (S15P21E201-561).
 *
 * <p>여기에 "이미 있나" 를 먼저 묻는 메서드를 두지 않는다. 그렇게 쓰면 조회와 쓰기 사이의
 * 틈에서 중복이 통과한다 — 막는 것은 기본키이고, 소비자는 저장이 거부되는 것으로 안다.
 */
public interface EventConsumptionRepository extends JpaRepository<EventConsumption, UUID> {

	/** 시간대별 조회 — 완료 기준의 "lag 과 실패 유형을 시간대별로 조회" 중 반영 쪽. */
	long countByConsumedAtBetween(OffsetDateTime from, OffsetDateTime to);
}
