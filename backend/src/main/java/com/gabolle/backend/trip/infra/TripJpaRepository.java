package com.gabolle.backend.trip.infra;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface TripJpaRepository extends JpaRepository<TripJpaEntity, UUID> {

	/**
	 * 정렬·상한·삭제 제외를 전부 질의에서 건다. 자바에서 자르면 여행 수백 개를 메모리로
	 * 가져오게 되고, 삭제 제외를 부르는 쪽으로 미루면 상한이 지워진 여행에 먼저 먹힌다.
	 */
	List<TripJpaEntity> findByTripIdInAndDeletedAtIsNullOrderByUpdatedAtDesc(
			Collection<UUID> tripIds, Pageable pageable);

	/** 가입할 때 "이 익명 세션이 만든 여행" 을 찾는 자리 — {@code ownerUserId} 에 세션 식별자가 들어간다. */
	List<TripJpaEntity> findByOwnerTypeAndOwnerUserId(String ownerType, UUID ownerUserId);
}
