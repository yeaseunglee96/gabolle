package com.gabolle.backend.trip.infra;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.trip.domain.TripMember;
import com.gabolle.backend.trip.domain.TripMembershipRepository;

/**
 * {@link TripMembershipRepository} 의 JPA 구현.
 *
 * <p>{@link #add} 는 {@code ON CONFLICT DO NOTHING} 으로 넣는다. {@code save} 로 넣고 제약 위반
 * 예외를 잡는 방식은 쓰지 않는다 — 그 예외가 트랜잭션을 롤백 전용으로 만들어, 호출자가 "이미
 * 참여" 를 성공으로 답하려 해도 커밋이 실패한다. 0행이면 이미 있었다는 뜻이다.
 */
@Repository
@Profile({ "db", "dev" })
public class JpaTripMembershipRepository implements TripMembershipRepository {

	private static final String INSERT_ON_CONFLICT_DO_NOTHING = """
			INSERT INTO trip_member (trip_member_id, trip_id, user_id, role, joined_at,
			                         trip_invite_id, invited_by, invited_at)
			VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8)
			ON CONFLICT (trip_id, user_id) DO NOTHING
			""";

	private final TripMemberJpaRepository memberJpaRepository;

	@PersistenceContext
	private EntityManager entityManager;

	public JpaTripMembershipRepository(TripMemberJpaRepository memberJpaRepository) {
		this.memberJpaRepository = memberJpaRepository;
	}

	@Override
	public Optional<TripMember> findMember(String tripId, String userId) {
		return this.memberJpaRepository.findByTripIdAndUserId(UUID.fromString(tripId), UUID.fromString(userId))
				.map(JpaTripMembershipRepository::toDomain);
	}

	/**
	 * {@code noRollbackFor} 가 필요하다. 이 메서드는 호출자의 트랜잭션에 참여하므로, 여기서 나간
	 * RuntimeException 은 그 공용 트랜잭션을 롤백 전용으로 만든다 — 호출자가 예외를 잡아 "이미
	 * 참여" 로 200 을 답해도 커밋 시점에 {@code UnexpectedRollbackException} 이 난다.
	 * {@link AlreadyMemberException} 은 실패가 아니라 사실이라 롤백 사유에서 뺀다.
	 */
	@Override
	@Transactional(noRollbackFor = AlreadyMemberException.class)
	public TripMember add(TripMember member) {
		int inserted = this.entityManager.createNativeQuery(INSERT_ON_CONFLICT_DO_NOTHING)
				.setParameter(1, UUID.fromString(member.tripMemberId()))
				.setParameter(2, UUID.fromString(member.tripId()))
				.setParameter(3, UUID.fromString(member.userId()))
				.setParameter(4, member.role().name())
				.setParameter(5, toOffset(member.joinedAt()))
				.setParameter(6, member.tripInviteId() == null ? null : UUID.fromString(member.tripInviteId()))
				.setParameter(7, member.invitedBy() == null ? null : UUID.fromString(member.invitedBy()))
				.setParameter(8, member.invitedAt() == null ? null : toOffset(member.invitedAt()))
				.executeUpdate();
		if (inserted == 0) {
			throw new AlreadyMemberException(member.tripId(), member.userId());
		}
		return member;
	}

	@Override
	@Transactional
	public Optional<TripMember> changeRole(String tripId, String userId, TripMember.Role newRole) {
		return this.memberJpaRepository.findByTripIdAndUserId(UUID.fromString(tripId), UUID.fromString(userId))
				.map(entity -> {
					entity.changeRole(newRole);
					return toDomain(this.memberJpaRepository.save(entity));
				});
	}

	@Override
	@Transactional
	public int remove(String tripId, String userId) {
		return this.entityManager
				.createQuery("DELETE FROM TripMemberJpaEntity m WHERE m.tripId = :tripId AND m.userId = :userId")
				.setParameter("tripId", UUID.fromString(tripId))
				.setParameter("userId", UUID.fromString(userId))
				.executeUpdate();
	}

	/** {@code JpaTripRepository.toDomain(TripMemberJpaEntity)} 와 같은 번역이다 — 한쪽만 고치면 어긋난다. */
	static TripMember toDomain(TripMemberJpaEntity e) {
		Instant joinedAt = e.joinedAt().toInstant();
		if (e.role() == TripMember.Role.OWNER) {
			return TripMember.owner(e.tripMemberId().toString(), e.tripId().toString(), e.userId().toString(),
					joinedAt);
		}
		return TripMember.invited(e.tripMemberId().toString(), e.tripId().toString(), e.userId().toString(),
				e.role(), joinedAt,
				e.tripInviteId() == null ? null : e.tripInviteId().toString(),
				e.invitedBy() == null ? null : e.invitedBy().toString(),
				e.invitedAt() == null ? null : e.invitedAt().toInstant());
	}

	private static OffsetDateTime toOffset(Instant instant) {
		return instant.atOffset(ZoneOffset.UTC);
	}
}
