package com.gabolle.backend.dish.repository;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.dish.domain.DishImageUsage;

public interface DishImageUsageRepository extends JpaRepository<DishImageUsage, UUID> {

	/** 이 사람이 {@code since} 이후로 그림을 몇 번 새로 만들었나. */
	long countByUserIdAndRequestedAtAfter(UUID userId, OffsetDateTime since);

	// @Transactional 을 여기 붙이는 이유는 MenuScanUsageRepository 의 같은 자리와 같다 —
	// @Modifying 질의는 트랜잭션을 요구하는데 Spring Data 가 직접 쓴 질의에는 안 걸어 준다.
	@Transactional
	@Modifying(flushAutomatically = true)
	@Query("DELETE FROM DishImageUsage u WHERE u.userId = :userId AND u.requestedAt < :before")
	int deleteExpiredFor(@Param("userId") UUID userId, @Param("before") OffsetDateTime before);
}
