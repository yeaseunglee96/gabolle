package com.gabolle.backend.menuscan.repository;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.menuscan.domain.MenuScanUsage;

public interface MenuScanUsageRepository extends JpaRepository<MenuScanUsage, UUID> {

	/** 이 사람이 {@code since} 이후로 몇 번 불렀나. 분 한도와 일 한도가 창만 바꿔 같이 쓴다. */
	long countByUserIdAndScannedAtAfter(UUID userId, OffsetDateTime since);

	/**
	 * 이 사람의 지난 기록을 지운다 — 부를 때마다 자기 것만 치운다. 한 번 쓰고 안 돌아온 사람의 행은
	 * 여기서 안 지워지고
	 * {@link com.gabolle.backend.menuscan.application.MenuScanUsageSweeper} 가 쓸어 간다.
	 */
	// @Transactional 을 여기 붙인다. @Modifying 질의는 트랜잭션을 요구하는데 Spring Data 는
	// 기본 CRUD 에만 걸어 주고, 시험이 호출부를 new 로 만들면 프록시가 없어 표시가 안 먹는다.
	@Transactional
	@Modifying(flushAutomatically = true)
	@Query("DELETE FROM MenuScanUsage u WHERE u.userId = :userId AND u.scannedAt < :before")
	int deleteExpiredFor(@Param("userId") UUID userId, @Param("before") OffsetDateTime before);

	/** 사용자를 가리지 않고 지난 기록 전부. 주기 청소가 쓴다. */
	@Transactional
	@Modifying(flushAutomatically = true)
	@Query("DELETE FROM MenuScanUsage u WHERE u.scannedAt < :before")
	int deleteExpired(@Param("before") OffsetDateTime before);
}
