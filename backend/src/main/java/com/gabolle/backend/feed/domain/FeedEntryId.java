package com.gabolle.backend.feed.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * 피드 줄 하나의 자리 — (세대, 위치).
 *
 * <p>홈과 커뮤니티가 <b>같은 키 모양</b>이라 한 클래스를 같이 쓴다. 표를 나눈 이유는
 * 가리키는 것이 달라서지(장소·일정 vs 글·글쓴이) 자리 매기는 방식이 달라서가 아니다.
 *
 * <p>🔴 위치({@code position})가 키의 일부인 것이 중요하다. 순서는 <b>만들 때 이미
 * 정해졌고</b>, 읽을 때 점수로 다시 정렬하지 않는다. 읽을 때 정렬하면 그 순간 계산이
 * 생기고, 이 티켓의 목적이 사라진다.
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
