package com.gabolle.backend.feed.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * 피드 줄 하나의 자리 — (세대, 위치). 홈과 커뮤니티가 같은 키 모양이라 한 클래스를 같이 쓴다.
 *
 * <p>위치가 키의 일부인 것이 중요하다. 순서는 만들 때 정해졌고, 읽을 때 점수로 다시
 * 정렬하지 않는다.
 */
@Embeddable
public class FeedEntryId implements Serializable {

	private static final long serialVersionUID = 1L;

	@Column(name = "build_id", nullable = false, updatable = false)
	private UUID buildId;

	@Column(name = "position", nullable = false, updatable = false)
	private int position;

	protected FeedEntryId() {
	}

	public FeedEntryId(UUID buildId, int position) {
		this.buildId = buildId;
		this.position = position;
	}

	public UUID getBuildId() {
		return this.buildId;
	}

	public int getPosition() {
		return this.position;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof FeedEntryId that)) {
			return false;
		}
		return this.position == that.position && Objects.equals(this.buildId, that.buildId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.buildId, this.position);
	}
}
