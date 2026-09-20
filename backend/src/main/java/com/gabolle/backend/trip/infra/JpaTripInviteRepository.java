package com.gabolle.backend.trip.infra;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import com.gabolle.backend.trip.domain.TripInvite;
import com.gabolle.backend.trip.domain.TripInviteRepository;

@Repository
@Profile({ "db", "dev" })
public class JpaTripInviteRepository implements TripInviteRepository {

	private final TripInviteJpaRepository jpaRepository;

	public JpaTripInviteRepository(TripInviteJpaRepository jpaRepository) {
		this.jpaRepository = jpaRepository;
	}

	@Override
	public TripInvite save(TripInvite invite) {
		this.jpaRepository.save(new TripInviteJpaEntity(
				UUID.fromString(invite.tripInviteId()), UUID.fromString(invite.tripId()), invite.token(),
				invite.role(), UUID.fromString(invite.createdBy()), toOffset(invite.createdAt()),
				toOffset(invite.expiresAt())));
		return invite;
	}

	@Override
	public Optional<TripInvite> findByToken(String token) {
		return this.jpaRepository.findByToken(token).map(JpaTripInviteRepository::toDomain);
	}

	private static TripInvite toDomain(TripInviteJpaEntity e) {
		return new TripInvite(e.tripInviteId().toString(), e.tripId().toString(), e.token(), e.role(),
				e.createdBy().toString(), e.createdAt().toInstant(), e.expiresAt().toInstant());
	}

	private static OffsetDateTime toOffset(Instant instant) {
		return instant.atOffset(ZoneOffset.UTC);
	}
}
