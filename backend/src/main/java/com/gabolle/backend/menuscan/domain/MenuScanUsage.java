package com.gabolle.backend.menuscan.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 메뉴판 읽기를 한 번 부른 기록. 부를 때마다 한 행이다 — «오늘 몇 번»을 숫자 하나로 더하는 모양으로는
 * 분 한도(최근 60초)를 셀 수 없다.
 *
 * <p>사진도 읽은 글자도 남기지 않는다. 이 표에 있는 것은 누가 언제 불렀나뿐이고, 한도를 세는 데
 * 나머지는 필요 없다.
 */
@Entity
@Table(name = "menu_scan_usage")
public class MenuScanUsage {

	@Id
	@Column(name = "menu_scan_usage_id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(name = "scanned_at", nullable = false, updatable = false)
	private OffsetDateTime scannedAt;

	protected MenuScanUsage() {
		// JPA 전용
	}

	private MenuScanUsage(UUID id, UUID userId, OffsetDateTime scannedAt) {
		this.id = id;
		this.userId = userId;
		this.scannedAt = scannedAt;
	}

	public static MenuScanUsage of(UUID id, UUID userId, OffsetDateTime scannedAt) {
		return new MenuScanUsage(id, userId, scannedAt);
	}

	public UUID getId() {
		return this.id;
	}

	public UUID getUserId() {
		return this.userId;
	}

	public OffsetDateTime getScannedAt() {
		return this.scannedAt;
	}
}
