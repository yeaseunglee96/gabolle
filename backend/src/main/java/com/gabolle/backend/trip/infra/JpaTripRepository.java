package com.gabolle.backend.trip.infra;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.trip.domain.PreferenceSnapshot;
import com.gabolle.backend.trip.domain.Trip;
import com.gabolle.backend.trip.domain.TripConstraint;
import com.gabolle.backend.trip.domain.TripMember;
import com.gabolle.backend.trip.domain.TripRepository;

/**
 * 여행 저장소 — S15P21E201-461. {@code trip} 표만 실제 PostgreSQL 로 옮겼다.
 *
 * <p>🔴 <b>범위를 일부러 좁혔다.</b> 멤버·취향/제약 스냅샷·멱등 키는 아직 이 표들처럼
 * 트랜잭션 하나로 묶이는 JPA 매핑이 없다 — {@link InMemoryTripRepository} 가 쓰던 것과
 * 같은 메모리 맵을 여기서도 그대로 쓴다. 그래서 <b>서버를 껐다 켜면 여행 조건 자체는
 * 남지만, 멤버·스냅샷·제약·멱등 키는 여전히 사라진다.</b> 다음 단계에서 나머지를
 * 옮긴다 — 한 번에 다 옮기면 리뷰가 안 된다.
 */
@Repository
@Profile({ "db", "dev" })
public class JpaTripRepository implements TripRepository {

	private final TripJpaRepository tripJpaRepository;

	// 🔴 아래 넷은 InMemoryTripRepository 와 똑같은 자리다 — 아직 DB 로 옮기지 않았다.
	private final Map<String, List<TripConstraint>> constraints = new ConcurrentHashMap<>();
	private final Map<String, List<TripMember>> members = new ConcurrentHashMap<>();
	private final Map<String, List<PreferenceSnapshot>> snapshots = new ConcurrentHashMap<>();
	private final Map<String, Binding> idempotency = new ConcurrentHashMap<>();

	private record Binding(String fingerprint, String tripId) {
	}

	public JpaTripRepository(TripJpaRepository tripJpaRepository) {
		this.tripJpaRepository = tripJpaRepository;
	}

	@Override
	@Transactional
	public Trip save(Trip trip, List<TripConstraint> tripConstraints, TripMember owner, PreferenceSnapshot snapshot) {
		tripJpaRepository.save(toEntity(trip));
		constraints.put(trip.tripId(), List.copyOf(tripConstraints));
		members.put(trip.tripId(), new ArrayList<>(List.of(owner)));
		snapshots.put(trip.tripId(), new ArrayList<>(List.of(snapshot)));
		return trip;
	}

	@Override
	public Optional<Trip> findById(String tripId) {
		return tripJpaRepository.findById(UUID.fromString(tripId)).map(JpaTripRepository::toDomain);
	}

	@Override
	public List<TripConstraint> findConstraints(String tripId) {
		return constraints.getOrDefault(tripId, List.of());
	}

	@Override
	public List<TripMember> findMembers(String tripId) {
		return members.getOrDefault(tripId, List.of());
	}

	@Override
	public Optional<PreferenceSnapshot> findSnapshot(String tripId, int version) {
		return snapshots.getOrDefault(tripId, List.of()).stream()
				.filter(s -> s.version() == version)
				.findFirst();
	}

	@Override
	public Optional<PreferenceSnapshot> findLatestSnapshot(String tripId) {
		return snapshots.getOrDefault(tripId, List.of()).stream()
				.max(Comparator.comparingInt(PreferenceSnapshot::version));
	}

	/**
	 * 🔴 InMemoryTripRepository 와 같은 방식(키마다 하나의 스레드만 들여보내는
	 * {@code compute})으로 경쟁을 막는다 — trip 저장이 DB 로 갔다고 이 규칙이 달라지지
	 * 않는다. 진짜 DB 트랜잭션 + {@code UNIQUE} 제약으로 이 자리를 대신하는 것은
	 * 멱등 키 표를 옮기는 다음 단계의 몫이다.
	 */
	@Override
	@Transactional
	public SaveOutcome saveWithIdempotency(String userId, String idempotencyKey, String fingerprint,
			Trip trip, List<TripConstraint> tripConstraints, TripMember owner, PreferenceSnapshot snapshot) {

		if (idempotencyKey == null || idempotencyKey.isBlank()) {
			save(trip, tripConstraints, owner, snapshot);
			return new SaveOutcome(trip, snapshot, true);
		}

		String[] winner = new String[1];

		idempotency.compute(keyOf(userId, idempotencyKey), (k, prior) -> {
			if (prior != null) {
				if (!prior.fingerprint().equals(fingerprint)) {
					throw new IdempotencyKeyConflictException(idempotencyKey);
				}
				winner[0] = prior.tripId();
				return prior;
			}
			save(trip, tripConstraints, owner, snapshot);
			winner[0] = trip.tripId();
			return new Binding(fingerprint, trip.tripId());
		});

		boolean created = trip.tripId().equals(winner[0]);
		if (created) {
			return new SaveOutcome(trip, snapshot, true);
		}
		Trip existing = findById(winner[0]).orElseThrow();
		return new SaveOutcome(existing, findLatestSnapshot(winner[0]).orElse(null), false);
	}

	private static String keyOf(String userId, String key) {
		return userId + "#" + key;
	}

	private static TripJpaEntity toEntity(Trip t) {
		return new TripJpaEntity(
				UUID.fromString(t.tripId()), UUID.fromString(t.createdBy()),
				t.startDate(), t.finishDate(),
				t.originLat(), t.originLng(),
				t.budgetKrw() == null ? null : t.budgetKrw().longValue(),
				t.partySize(), t.timeWindow(), t.timezone(), t.status(),
				toOffset(t.createdAt()), toOffset(t.updatedAt()), toOffset(t.deletedAt()));
	}

	private static Trip toDomain(TripJpaEntity e) {
		return Trip.builder()
				.tripId(e.tripId().toString())
				.createdBy(e.ownerUserId().toString())
				.startDate(e.startDate())
				.finishDate(e.endDate())
				.originLat(e.originLat())
				.originLng(e.originLng())
				.budgetKrw(e.budgetKrw() == null ? null : e.budgetKrw().intValue())
				.partySize(e.partySize() == null ? 1 : e.partySize())
				.timeWindow(e.timeWindow())
				.timezone(e.timezone())
				.status(e.status())
				.createdAt(toInstant(e.createdAt()))
				.updatedAt(toInstant(e.updatedAt()))
				.deletedAt(toInstant(e.deletedAt()))
				.build();
	}

	private static OffsetDateTime toOffset(Instant instant) {
		return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
	}

	private static Instant toInstant(OffsetDateTime offsetDateTime) {
		return offsetDateTime == null ? null : offsetDateTime.toInstant();
	}
}
