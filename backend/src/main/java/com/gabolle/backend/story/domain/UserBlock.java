package com.gabolle.backend.story.domain;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * 차단 관계 한 줄 — {@code blocker} 가 {@code blocked} 를 차단했다 (S15P21E201-990).
 *
 * <p>🔴 <b>방향이 흔한 것과 반대다.</b> A 가 B 를 차단하면 <b>B 가 A 를 못 본다.</b> "내가 이 사람을
 * 안 본다" 가 아니라 "이 사람에게 내 것을 안 보여준다" 이다. A 는 B 를 계속 볼 수 있다 — B 가 A 를
 * 차단하지 않았다면. 읽는 사람이 반대로 짐작하기 쉬운 자리라 여기에 적어 둔다.
 *
 * <p>🔴 기본키가 두 사람의 쌍이다. 같은 요청이 두 번 와도 두 줄이 생길 수 없는 것은 코드가 아니라
 * 이 키가 보장한다. 코드는 "이미 있으면 그대로 두고 성공으로 답한다" 만 한다 —
 * {@link UserFollow} 가 같은 이유로 같은 모양이다.
 */
@Entity
@Table(name = "user_block")
public class UserBlock {

	@EmbeddedId
	private Key key;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected UserBlock() {
	}

	public UserBlock(UUID blockerUserId, UUID blockedUserId, Instant createdAt) {
		if (blockerUserId.equals(blockedUserId)) {
			throw new SelfBlockException(blockerUserId);
		}
		this.key = new Key(blockerUserId, blockedUserId);
		this.createdAt = createdAt;
	}

	public UUID getBlockerUserId() { return key.blockerUserId; }
	public UUID getBlockedUserId() { return key.blockedUserId; }
	public Instant getCreatedAt()  { return createdAt; }

	@Embeddable
	public static class Key implements Serializable {

		@Column(name = "blocker_user_id", nullable = false)
		private UUID blockerUserId;

		@Column(name = "blocked_user_id", nullable = false)
		private UUID blockedUserId;

		protected Key() {
		}

		public Key(UUID blockerUserId, UUID blockedUserId) {
			this.blockerUserId = blockerUserId;
			this.blockedUserId = blockedUserId;
		}

		public UUID blockerUserId() { return blockerUserId; }
		public UUID blockedUserId() { return blockedUserId; }

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (!(o instanceof Key other)) {
				return false;
			}
			return blockerUserId.equals(other.blockerUserId) && blockedUserId.equals(other.blockedUserId);
		}

		@Override
		public int hashCode() {
			return Objects.hash(blockerUserId, blockedUserId);
		}
	}

	/** 자기 자신을 차단하려 했다 — 400 으로 답할 자리다. */
	public static class SelfBlockException extends RuntimeException {

		public SelfBlockException(UUID userId) {
			super("자기 자신은 차단할 수 없습니다.");
		}
	}

	/**
	 * 이 사람이 나를 차단했다 — 403 으로 답할 자리다.
	 *
	 * <p>🔴 404 가 아니라 403 인 이유. 팀이 <b>없는 사람인 척하지 않기로</b> 정했다(S15P21E201-990).
	 * 화면이 「차단되어 볼 수 없습니다」를 띄우려면 "없다" 와 "막혔다" 를 가를 수 있어야 한다.
	 * 이것이 차단 사실을 상대에게 알린다는 뜻이기도 하다 — 혼란은 없애지만 다른 계정을 만들
	 * 동기를 준다. 그 트레이드오프는 티켓에 결정 사항으로 적어 두었고, 바꾸려면 여기와 화면
	 * 문구만 고치면 된다.
	 */
	public static class BlockedByUserException extends RuntimeException {

		public BlockedByUserException(UUID userId) {
			super("차단되어 볼 수 없습니다.");
		}
	}
}
