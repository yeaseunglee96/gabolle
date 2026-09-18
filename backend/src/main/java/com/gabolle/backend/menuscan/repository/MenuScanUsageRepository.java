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
	 * 이 사람의 지난 기록을 지운다 — 부를 때마다 자기 것만 치운다.
	 *
	 * <p>돌아오는 사용자의 행은 이것으로 계속 묶인다. 하지만 <b>한 번 쓰고 안 돌아온</b>
	 * 사람의 행은 여기서 영영 안 지워진다 — 그 자리는
	 * {@link com.gabolle.backend.menuscan.application.MenuScanUsageSweeper} 가 쓸어 간다.
	 * 메모리 맵 시절에 정확히 이 절반이 없어서 자리가 계속 쌓였다.
	 */
	//
	// @Transactional 을 여기 붙인다 (S15P21E201-1037 에서 같은 것으로 CI 가 빨개졌다).
	// @Modifying 질의는 트랜잭션을 요구하는데 Spring Data 는 기본 CRUD 에만 걸어 주고
	// 직접 쓴 질의에는 안 걸어 준다. 한도 계산기와 청소기가 둘 다 @Transactional 이지만,
	// 시험이 그것들을 스프링 빈이 아니라 new 로 만들면 프록시가 없어 표시가 안 먹는다.
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
