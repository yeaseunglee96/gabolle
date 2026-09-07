package com.gabolle.backend.trip.infra;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.trip.domain.PersonalizationScope;
import com.gabolle.backend.trip.domain.PreferenceSnapshot;
import com.gabolle.backend.trip.domain.Trip;
import com.gabolle.backend.trip.domain.TripConstraint;
import com.gabolle.backend.trip.domain.TripMember;
import com.gabolle.backend.trip.domain.TripRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * 여행 저장소 — S15P21E201-461. trip·trip_member·preference_snapshot·preference_answer·
 * constraint_snapshot·constraint_answer·trip_idempotency 를 전부 PostgreSQL 로 옮겼다.
 *
 * 처음엔 trip 표만 옮기고 나머지는 메모리로 남겼는데(리뷰 범위 조절), 고지혁 님이
 * 운영에서 preference_answer=0 · constraint_answer=0 을 실측해 "여행은 저장되는데
 * 취향·제약이 조용히 버려진다" 는 것을 찾았다. M1 판정이 오늘이라 이번에 마저 옮긴다.
 *
 * evidenceStatus·operator 는 왕복하지 않는다 — constraint_answer 표에 그 두 칸이 없다.
 * 고지혁 님 지적대로, 이건 유실이 아니라 설계다. operator 는 constraint_key 자체가
 * 이미 뜻을 담고 있다(예: MAX_WALKING_METERS 는 그 이름부터 "이하"다) — 따로 저장할
 * 값이 아니다. evidenceStatus 는 사용자 입력이 아니라 place 데이터의 속성이다(명세
 * 6.2) — 사용자가 "내 알레르기 신고가 검증됐다"를 스스로 표시할 자리가 없다. 다시
 * 읽어올 때는 evidenceStatus 를 NEEDS_REVIEW 로, operator 는 종류별 관례값으로
 * 채운다 — ALLERGY·DIET 는 EXCLUDES, MOBILITY 는 LTE.
 *
 * 멱등 키는 SAVEPOINT 대신 ON CONFLICT DO NOTHING 을 쓴다 — JpaItineraryRepository 에서
 * SAVEPOINT(PROPAGATION_NESTED)가 이 환경의 트랜잭션 매니저에서 실제로 안 먹히는 것을
 * CI 로 확인했다. 여기서는 처음부터 그 문제를 피한다.
 */
@Repository
@Profile({ "db", "dev" })
public class JpaTripRepository implements TripRepository {

	private final TripJpaRepository tripJpaRepository;
	private final TripMemberJpaRepository memberJpaRepository;
	private final PreferenceSnapshotJpaRepository preferenceSnapshotJpaRepository;
	private final PreferenceAnswerJpaRepository preferenceAnswerJpaRepository;
	private final ConstraintSnapshotJpaRepository constraintSnapshotJpaRepository;
	private final ConstraintAnswerJpaRepository constraintAnswerJpaRepository;

	@PersistenceContext
	private EntityManager entityManager;

	public JpaTripRepository(TripJpaRepository tripJpaRepository,
			TripMemberJpaRepository memberJpaRepository,
			PreferenceSnapshotJpaRepository preferenceSnapshotJpaRepository,
			PreferenceAnswerJpaRepository preferenceAnswerJpaRepository,
			ConstraintSnapshotJpaRepository constraintSnapshotJpaRepository,
			ConstraintAnswerJpaRepository constraintAnswerJpaRepository) {
		this.tripJpaRepository = tripJpaRepository;
		this.memberJpaRepository = memberJpaRepository;
		this.preferenceSnapshotJpaRepository = preferenceSnapshotJpaRepository;
		this.preferenceAnswerJpaRepository = preferenceAnswerJpaRepository;
		this.constraintSnapshotJpaRepository = constraintSnapshotJpaRepository;
		this.constraintAnswerJpaRepository = constraintAnswerJpaRepository;
	}

	@Override
	@Transactional
	public Trip save(Trip trip, List<TripConstraint> tripConstraints, TripMember owner, PreferenceSnapshot snapshot) {
		UUID tripId = UUID.fromString(trip.tripId());
		UUID ownerUserId = UUID.fromString(owner.userId());

		tripJpaRepository.save(toEntity(trip));
		memberJpaRepository.save(toMemberEntity(owner));

		UUID prefSnapshotId = UUID.fromString(snapshot.snapshotId());
		preferenceSnapshotJpaRepository.save(new PreferenceSnapshotJpaEntity(
				prefSnapshotId, ownerUserId, tripId, snapshot.version(), snapshot.scope(),
				toOffset(snapshot.createdAt())));
		for (PreferenceSnapshot.PreferenceAnswer answer : snapshot.answers()) {
			preferenceAnswerJpaRepository.save(new PreferenceAnswerJpaEntity(
					UUID.randomUUID(), prefSnapshotId, answer.dimension(), answer.valueJson(),
					answer.status(), toOffset(snapshot.createdAt())));
		}

		if (!tripConstraints.isEmpty()) {
			UUID constraintSnapshotId = UUID.randomUUID();
			constraintSnapshotJpaRepository.save(new ConstraintSnapshotJpaEntity(
					constraintSnapshotId, ownerUserId, tripId, 1, PersonalizationScope.TRIP,
					toOffset(trip.createdAt())));
			for (TripConstraint c : tripConstraints) {
				constraintAnswerJpaRepository.save(new ConstraintAnswerJpaEntity(
						UUID.fromString(c.constraintId()), constraintSnapshotId, c.type(), c.constraintKey(),
						valueJsonOf(c), c.severity() == TripConstraint.Severity.HARD, c.answerStatus(),
						c.dietRequirement(), toOffset(trip.createdAt())));
			}
		}

		return trip;
	}

	@Override
	public Optional<Trip> findById(String tripId) {
		return tripJpaRepository.findById(UUID.fromString(tripId)).map(JpaTripRepository::toDomain);
	}

	@Override
	public List<TripConstraint> findConstraints(String tripId) {
		UUID id = UUID.fromString(tripId);
		List<ConstraintSnapshotJpaEntity> snapshots = constraintSnapshotJpaRepository.findByTripId(id);
		if (snapshots.isEmpty()) {
			return List.of();
		}
		ConstraintSnapshotJpaEntity snapshot = snapshots.get(0);
		return constraintAnswerJpaRepository.findByConstraintSnapshotId(snapshot.constraintSnapshotId()).stream()
				.map(e -> toDomain(e, tripId))
				.toList();
	}

	@Override
	public List<TripMember> findMembers(String tripId) {
		return memberJpaRepository.findByTripId(UUID.fromString(tripId)).stream()
				.map(JpaTripRepository::toDomain)
				.toList();
	}

	@Override
	public Optional<PreferenceSnapshot> findSnapshot(String tripId, int version) {
		return preferenceSnapshotJpaRepository.findByTripIdAndVersion(UUID.fromString(tripId), version)
				.map(this::toDomain);
	}

	@Override
	public Optional<PreferenceSnapshot> findLatestSnapshot(String tripId) {
		return preferenceSnapshotJpaRepository.findTopByTripIdOrderByVersionDesc(UUID.fromString(tripId))
				.map(this::toDomain);
	}

	@Override
	public Optional<PreferenceSnapshot> findSnapshotById(String preferenceSnapshotId) {
		// 🔴 S15P21E201-604 — 추천 Job 이 기록해 둔 그 판을 직접 읽는다. findLatestSnapshot 을
		// 쓰면 Job 이 실행되기 전에 사용자가 취향을 다시 답했을 때 "그때 그 판" 이 아니라
		// "지금 최신 판" 을 읽게 된다.
		return preferenceSnapshotJpaRepository.findById(UUID.fromString(preferenceSnapshotId))
				.map(this::toDomain);
	}

	@Override
	public List<TripConstraint> findConstraintsBySnapshotId(String constraintSnapshotId) {
		// 🔴 findConstraints(tripId) 를 재사용하지 않는다 — 그쪽은 findByTripId(id).get(0) 로
		// 첫 번째 스냅샷을 집는데, 추천 Job 이 기록해 둔 스냅샷과 다를 수 있다.
		UUID snapshotId = UUID.fromString(constraintSnapshotId);
		String tripId = constraintSnapshotJpaRepository.findById(snapshotId)
				.map(e -> e.tripId() == null ? null : e.tripId().toString())
				.orElse(null);
		return constraintAnswerJpaRepository.findByConstraintSnapshotId(snapshotId).stream()
				.map(e -> toDomain(e, tripId))
				.toList();
	}

	@Override
	public Optional<String> findLatestConstraintSnapshotId(String tripId) {
		return constraintSnapshotJpaRepository.findTopByTripIdOrderByVersionDesc(UUID.fromString(tripId))
				.map(e -> e.constraintSnapshotId().toString());
	}

	@Override
	@Transactional
	public SaveOutcome saveWithIdempotency(String userId, String idempotencyKey, String fingerprint,
			Trip trip, List<TripConstraint> tripConstraints, TripMember owner, PreferenceSnapshot snapshot) {

		if (idempotencyKey == null || idempotencyKey.isBlank()) {
			save(trip, tripConstraints, owner, snapshot);
			return new SaveOutcome(trip, snapshot, true);
		}

		int inserted = entityManager.createNativeQuery(
				"INSERT INTO trip_idempotency (user_id, idempotency_key, request_fingerprint, trip_id, created_at) "
						+ "VALUES (?1, ?2, ?3, ?4, ?5) ON CONFLICT (user_id, idempotency_key) DO NOTHING")
				.setParameter(1, UUID.fromString(userId))
				.setParameter(2, idempotencyKey)
				.setParameter(3, fingerprint)
				.setParameter(4, UUID.fromString(trip.tripId()))
				.setParameter(5, toOffset(trip.createdAt()))
				.executeUpdate();

		if (inserted == 1) {
			save(trip, tripConstraints, owner, snapshot);
			return new SaveOutcome(trip, snapshot, true);
		}

		Object[] row = (Object[]) entityManager.createNativeQuery(
				"SELECT request_fingerprint, trip_id FROM trip_idempotency "
						+ "WHERE user_id = ?1 AND idempotency_key = ?2")
				.setParameter(1, UUID.fromString(userId))
				.setParameter(2, idempotencyKey)
				.getSingleResult();

		String existingFingerprint = (String) row[0];
		UUID existingTripId = (UUID) row[1];

		if (!existingFingerprint.equals(fingerprint)) {
			throw new IdempotencyKeyConflictException(idempotencyKey);
		}

		Trip existingTrip = findById(existingTripId.toString()).orElseThrow();
		PreferenceSnapshot existingSnapshot = findLatestSnapshot(existingTripId.toString()).orElse(null);
		return new SaveOutcome(existingTrip, existingSnapshot, false);
	}

	private static TripJpaEntity toEntity(Trip t) {
		return new TripJpaEntity(
				UUID.fromString(t.tripId()), UUID.fromString(t.createdBy()),
				t.startDate(), t.finishDate(),
				t.originLat(), t.originLng(),
				t.budgetKrw() == null ? null : t.budgetKrw().longValue(),
				t.partySize(), t.timeWindow(), t.timezone(),
				t.travelModes(), t.timeWindowStart(), t.timeWindowEnd(), t.status(),
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
				.timeWindowStart(e.timeWindowStart())
				.timeWindowEnd(e.timeWindowEnd())
				.travelModes(e.travelModes())
				.timezone(e.timezone())
				.status(e.status())
				.createdAt(toInstant(e.createdAt()))
				.updatedAt(toInstant(e.updatedAt()))
				.deletedAt(toInstant(e.deletedAt()))
				.build();
	}

	private static TripMemberJpaEntity toMemberEntity(TripMember m) {
		return new TripMemberJpaEntity(
				UUID.fromString(m.tripMemberId()), UUID.fromString(m.tripId()), UUID.fromString(m.userId()),
				m.role(), toOffset(m.joinedAt()));
	}

	private static TripMember toDomain(TripMemberJpaEntity e) {
		Instant at = toInstant(e.joinedAt());
		if (e.role() == TripMember.Role.OWNER) {
			return TripMember.owner(e.tripMemberId().toString(), e.tripId().toString(), e.userId().toString(), at);
		}
		return TripMember.invited(e.tripMemberId().toString(), e.tripId().toString(), e.userId().toString(),
				e.role(), at);
	}

	private PreferenceSnapshot toDomain(PreferenceSnapshotJpaEntity e) {
		List<PreferenceSnapshot.PreferenceAnswer> answers = preferenceAnswerJpaRepository
				.findByPreferenceSnapshotId(e.preferenceSnapshotId()).stream()
				.map(a -> new PreferenceSnapshot.PreferenceAnswer(a.dimension(), a.valueJson(), a.answerStatus()))
				.toList();
		return new PreferenceSnapshot(e.preferenceSnapshotId().toString(),
				e.tripId() == null ? null : e.tripId().toString(),
				e.version(), answers, e.scope(), List.of(), toInstant(e.createdAt()));
	}

	private static String valueJsonOf(TripConstraint c) {
		if (c.threshold() != null) {
			return "{\"meters\":" + c.threshold() + "}";
		}
		if (c.value() != null && !c.value().isBlank()) {
			return "\"" + c.value().replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
		}
		return null;
	}

	private static TripConstraint toDomain(ConstraintAnswerJpaEntity e, String tripId) {
		return new TripConstraint(
				e.constraintAnswerId().toString(), tripId, e.constraintType(), e.constraintKey(),
				e.hard() ? TripConstraint.Severity.HARD : TripConstraint.Severity.SOFT,
				defaultOperatorFor(e.constraintType()),
				null, extractMeters(e.valueJson()),
				TripConstraint.EvidenceStatus.NEEDS_REVIEW,
				e.answerStatus(), PersonalizationScope.TRIP, e.dietRequirement());
	}

	private static String defaultOperatorFor(String type) {
		return "MOBILITY".equalsIgnoreCase(type) ? "LTE" : "EXCLUDES";
	}

	private static Double extractMeters(String valueJson) {
		if (valueJson == null) {
			return null;
		}
		int idx = valueJson.indexOf("meters");
		if (idx < 0) {
			return null;
		}
		StringBuilder digits = new StringBuilder();
		boolean seenDigit = false;
		for (int i = idx; i < valueJson.length(); i++) {
			char c = valueJson.charAt(i);
			if (Character.isDigit(c) || c == '.') {
				digits.append(c);
				seenDigit = true;
			}
			else if (seenDigit) {
				break;
			}
		}
		return digits.length() == 0 ? null : Double.valueOf(digits.toString());
	}

	private static OffsetDateTime toOffset(Instant instant) {
		return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
	}

	private static Instant toInstant(OffsetDateTime offsetDateTime) {
		return offsetDateTime == null ? null : offsetDateTime.toInstant();
	}
}
