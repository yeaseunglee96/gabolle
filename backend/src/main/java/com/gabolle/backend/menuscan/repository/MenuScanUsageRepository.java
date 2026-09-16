package com.gabolle.backend.menuscan.repository;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.gabolle.backend.menuscan.domain.MenuScanUsage;

public interface MenuScanUsageRepository extends JpaRepository<MenuScanUsage, UUID> {

	/** 이 사람이 {@code since} 이후로 몇 번 불렀나. 분 한도와 일 한도가 창만 바꿔 같이 쓴다. */
	long countByUserIdAndScannedAtAfter(UUID userId, OffsetDateTime since);

	/**
	 * 이 사람의 지난 기록을 지운다 — 부를 때마다 자기 것만 치운다.
	 *
	 * <p>🔴 돌아오는 사용자의 행은 이것으로 계속 묶인다. 하지만 <b>한 번 쓰고 안 돌아온</b>
	 * 사람의 행은 여기서 영영 안 지워진다 — 그 자리는
	 * {@link com.gabolle.backend.menuscan.application.MenuScanUsageSweeper} 가 쓸어 간다.
	 * 메모리 맵 시절에 정확히 이 절반이 없어서 자리가 계속 쌓였다.
	 */
	@Modifying
	@Query("DELETE FROM MenuScanUsage u WHERE u.userId = :userId AND u.scannedAt < :before")
	int deleteExpiredFor(@Param("userId") UUID userId, @Param("before") OffsetDateTime before);

	/** 사용자를 가리지 않고 지난 기록 전부. 주기 청소가 쓴다. */
	@Modifying
	@Query("DELETE FROM MenuScanUsage u WHERE u.scannedAt < :before")
	int deleteExpired(@Param("before") OffsetDateTime before);
}
