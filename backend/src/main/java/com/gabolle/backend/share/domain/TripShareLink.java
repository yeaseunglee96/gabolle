package com.gabolle.backend.share.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 읽기 전용 공유 주소 — S15P21E201-330 (F-COL-03). {@code trip_share_link} 표 (V20260907040000).
 *
 * <p>공유 링크는 대화방과 게시물에 남는다. 기한이 없으면 여행이 끝나고 한참 뒤에도 아무나 연다.
 * 그래서 만드는 시점부터 {@link #TTL 30일} 뒤 만료된다. 만료는 행을 지우는 것이 아니라
 * {@code expiresAt} 을 지나는 것이다 — 그래야 "있었는데 끝났다"(410)와 "없다"(404)를 화면이 다르게 그린다.
 *
 * <p>🔴 이 표를 여는 사람은 로그인하지 않았다. 응답은 {@code SharedItineraryResponse} 만 쓴다 —
 * 원본 응답에서 칸을 비우는 것이 아니라 보낼 것만 담아 새로 만든 모양이다(S15P21E201-332).
 *
 * <p>{@code story} 모듈과 같은 방식으로 JPA 엔티티를 도메인에 둔다. 표 하나에 규칙이 둘(만료·열람 수)
 * 뿐이라 도메인/인프라를 가르는 것이 얻는 것보다 파일만 늘린다.
 */
@Entity
@Table(name = "trip_share_link")
public class TripShareLink {

	/** 공유 주소의 수명. 티켓이 "30일" 로 못 박았다. */
	public static final Duration TTL = Duration.ofDays(30);

	@Id
	@Column(name = "trip_share_link_id")
	private UUID tripShareLinkId;

	@Column(name = "trip_id", nullable = false, updatable = false)
	private UUID tripId;

	@Column(name = "token", nullable = false, length = 64, updatable = false)
	private String token;

	@Column(name = "created_by", nullable = false, updatable = false)
	private UUID createdBy;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "expires_at", nullable = false, updatable = false)
	private Instant expiresAt;

	@Column(name = "view_count", nullable = false)
	private int viewCount;

	@Column(name = "last_viewed_at")
	private Instant lastViewedAt;

	protected TripShareLink() {
		// JPA 전용
	}

	private TripShareLink(UUID tripShareLinkId, UUID tripId, String token, UUID createdBy, Instant createdAt,
			Instant expiresAt) {
		if (token == null || token.isBlank()) {
			throw new IllegalArgumentException("공유 표(token)는 비울 수 없다");
		}
		if (!expiresAt.isAfter(createdAt)) {
			throw new IllegalArgumentException("만료 시각은 발급 시각보다 뒤여야 한다");
		}
		this.tripShareLinkId = tripShareLinkId;
		this.tripId = tripId;
		this.token = token;
		this.createdBy = createdBy;
		this.createdAt = createdAt;
		this.expiresAt = expiresAt;
		this.viewCount = 0;
	}

	/** 지금 발급하는 공유 주소. 만료는 발급 시각 + {@link #TTL}. */
	public static TripShareLink issue(UUID tripId, String token, UUID createdBy, Instant now) {
		return new TripShareLink(UUID.randomUUID(), tripId, token, createdBy, now, now.plus(TTL));
	}

	public boolean isExpiredAt(Instant now) {
		return !now.isBefore(this.expiresAt);
	}

	/**
	 * 한 번 열렸다. 🔴 조회와 같은 트랜잭션에서 부른다 — 따로 세면 "조회는 됐는데 수가 안 오른"
	 * 상태가 생기고, 완료 기준 "두 번 열면 조회 수가 2" 가 어긋난다.
	 */
	public void recordView(Instant now) {
		this.viewCount++;
		this.lastViewedAt = now;
	}

	public UUID getTripShareLinkId() { return tripShareLinkId; }
	public UUID getTripId() { return tripId; }
	public String getToken() { return token; }
	public UUID getCreatedBy() { return createdBy; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getExpiresAt() { return expiresAt; }
	public int getViewCount() { return viewCount; }
	public Instant getLastViewedAt() { return lastViewedAt; }
}
