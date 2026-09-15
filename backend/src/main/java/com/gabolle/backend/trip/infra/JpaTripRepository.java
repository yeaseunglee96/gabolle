package com.gabolle.backend.trip.infra;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
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

	/**
	 * S15P21E201-746 — 여행 삭제.
	 *
	 * <p>🔴 <b>{@code toEntity(trip)} 로 만든 객체를 저장하지 않는다.</b> 이 클래스는
	 * {@code version}·{@code origin_source}·{@code origin_area_code} 를 일부러 매핑하지
	 * 않는데({@code TripJpaEntity} 주석), 매핑 안 된 칸이 있는 상태에서 통째로 덮어쓰는
	 * 방식은 나중에 누가 그 칸을 매핑하는 순간 조용히 값을 날린다. 대신 이미 저장된 행을
	 * 읽어 지운 시각만 찍는다.
	 *
	 * <p>없는 여행이면 {@link IllegalStateException} 이다. 삭제 경로는 이미 회원 여부까지
	 * 판정한 뒤에 오므로 여기서 못 찾는 것은 정상 흐름이 아니라 어긋남이다 — 조용히
	 * 넘기면 사용자에게는 지워졌다고 답하고 표에는 그대로 남는다.
	 */
	@Override
	@Transactional
	public void softDelete(Trip trip) {
		TripJpaEntity entity = tripJpaRepository.findById(UUID.fromString(trip.tripId()))
				.orElseThrow(() -> new IllegalStateException("지우려는 여행이 표에 없다: tripId=" + trip.tripId()));
		entity.markDeleted(toOffset(trip.deletedAt()), toOffset(trip.updatedAt()));
		tripJpaRepository.save(entity);
	}

	/**
	 * 상태 칸만 옮긴다 — S15P21E201-964. 위 {@link #softDelete} 와 같은 이유로 읽어서
	 * 고치지, {@code toEntity(trip)} 로 만든 객체를 통째로 덮어쓰지 않는다.
	 *
	 * <p>🔴 {@code @Transactional} 을 새로 열지 않는다. 이 자리를 부르는 것은 일정이
	 * 처음 저장되는 트랜잭션 안이고({@code ItineraryDraftService.persist}), 여기서 새
	 * 트랜잭션을 열면 일정 저장이 뒤에서 굴러떨어져도 상태만 READY 로 남는다.
	 * 저장 자체는 바깥 트랜잭션이 끝날 때 함께 반영된다.
	 */
	@Override
	public void updateStatus(Trip trip) {
		TripJpaEntity entity = tripJpaRepository.findById(UUID.fromString(trip.tripId()))
				.orElseThrow(() -> new IllegalStateException("상태를 바꾸려는 여행이 표에 없다: tripId=" + trip.tripId()));
		entity.changeStatus(trip.status(), toOffset(trip.updatedAt()));
		tripJpaRepository.save(entity);
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

	/**
	 * S15P21E201-738 — 내 여행 목록.
	 *
	 * <p>🔴 질의는 <b>여행 수와 무관하게 둘</b>이다. 참여 행을 한 번 읽어 여행 식별자와
	 * 역할을 얻고, 그 식별자 묶음으로 여행을 한 번 읽는다. 여행마다 {@code findById} 를
	 * 부르면 목록 하나에 질의가 N+1 이 된다.
	 *
	 * <p>정렬은 질의가 이미 {@code updated_at} 내림차순으로 해 두었다. 그 순서를 그대로
	 * 유지하려고 역할은 <b>맵으로 찾아 붙이기만</b> 한다 — 여기서 다시 정렬하면 질의가
	 * 정한 순서를 두 곳에서 정하게 된다.
	 */
	@Override
	@Transactional(readOnly = true)
	public List<TripRepository.MemberTrip> findTripsForMember(String userId, int limit) {
		if (limit <= 0) {
			return List.of();
		}

		Map<UUID, TripMember.Role> roleByTripId = memberJpaRepository.findByUserId(UUID.fromString(userId)).stream()
				.collect(Collectors.toMap(TripMemberJpaEntity::tripId, TripMemberJpaEntity::role,
						// uq_trip_member (trip_id, user_id) 가 한 사람당 한 행을 보장하므로
						// 충돌은 생기지 않는다. 그래도 병합 규칙을 비워 두지 않는다 —
						// 제약이 사라진 날 조용히 예외로 죽는 것보다 먼저 들어온 값을 쓴다.
						(first, second) -> first));

		if (roleByTripId.isEmpty()) {
			return List.of();
		}

		return tripJpaRepository
				.findByTripIdInAndDeletedAtIsNullOrderByUpdatedAtDesc(roleByTripId.keySet(), Pageable.ofSize(limit))
				.stream()
				.map(e -> new TripRepository.MemberTrip(toDomain(e), roleByTripId.get(e.tripId())))
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
	public Optional<PreferenceSnapshot> findUserDefaults(String userId) {
		return preferenceSnapshotJpaRepository
				.findTopByUserIdAndTripIdIsNullOrderByVersionDesc(UUID.fromString(userId))
				.map(this::toDomain);
	}

	@Override
	@Transactional
	public PreferenceSnapshot saveUserDefaults(String userId,
			List<PreferenceSnapshot.PreferenceAnswer> answers, java.time.Instant at) {

		UUID ownerUserId = UUID.fromString(userId);
		// 🔴 마지막 판 + 1. 동시에 두 번 저장하면 uq_preference_snapshot_user 가 하나를
		//    거부한다 — 그 거부가 곧 직렬화이고, 여기서 락을 따로 걸지 않는 이유다.
		int nextVersion = preferenceSnapshotJpaRepository
				.findTopByUserIdAndTripIdIsNullOrderByVersionDesc(ownerUserId)
				.map(e -> e.version() + 1)
				.orElse(1);

		UUID snapshotId = UUID.randomUUID();
		// 🔴 trip_id 는 null 이다. ck_preference_snapshot_scope_trip 이
		//    (scope='TRIP') = (trip_id IS NOT NULL) 을 요구하므로 USER 는 반드시 null 이어야 한다.
		preferenceSnapshotJpaRepository.save(new PreferenceSnapshotJpaEntity(
				snapshotId, ownerUserId, null, nextVersion, PersonalizationScope.USER, toOffset(at)));
		for (PreferenceSnapshot.PreferenceAnswer answer : answers) {
			preferenceAnswerJpaRepository.save(new PreferenceAnswerJpaEntity(
					UUID.randomUUID(), snapshotId, answer.dimension(), answer.valueJson(),
					answer.status(), toOffset(at)));
		}
		return new PreferenceSnapshot(snapshotId.toString(), null, nextVersion, answers,
				PersonalizationScope.USER, List.of(), at);
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

	/**
	 * S15P21E201-317 — 가입 시 익명 여행 승계.
	 *
	 * <p>🔴 <b>네이티브 SQL 로만 옮긴다.</b> {@code trip.owner_user_id}·{@code trip_member.user_id}
	 * 는 둘 다 {@code updatable = false} 다(엔티티 주석 참고) — Hibernate 가 엔티티를 고쳐 저장하는
	 * 평소 경로로는 그 두 칸을 <b>조용히 안 바꾼다.</b> 그래서 그 경로를 안 쓰고
	 * {@link EntityManager#createNativeQuery(String)} 로 직접 UPDATE 한다.
	 *
	 * <p>여행마다 trip → trip_member(OWNER 행) → preference_snapshot → constraint_snapshot
	 * 순으로 옮긴다. 뒤의 두 스냅샷 표는 도메인({@link Trip}·{@link TripMember})에 없는,
	 * 순수 인프라 칸이라({@code JpaTripRepository.save} 가 저장할 때만 쓴다) 여기서도
	 * SQL로만 다룬다 — 남겨 두면 승계된 여행의 취향·제약이 사라진 익명 세션 UUID를
	 * 계속 가리키는 채로 남는다.
	 */
	@Override
	@Transactional
	public int claimAnonymousTrips(String sessionId, String newOwnerId, Instant at) {
		UUID session = UUID.fromString(sessionId);
		UUID newOwner = UUID.fromString(newOwnerId);
		OffsetDateTime now = toOffset(at);

		List<TripJpaEntity> anonymousTrips = tripJpaRepository.findByOwnerTypeAndOwnerUserId("ANONYMOUS", session);
		if (anonymousTrips.isEmpty()) {
			return 0;
		}

		for (TripJpaEntity entity : anonymousTrips) {
			UUID tripId = entity.tripId();

			entityManager.createNativeQuery(
					"UPDATE trip SET owner_user_id = ?1, owner_type = 'USER', updated_at = ?2 WHERE trip_id = ?3")
					.setParameter(1, newOwner).setParameter(2, now).setParameter(3, tripId)
					.executeUpdate();

			entityManager.createNativeQuery(
					"UPDATE trip_member SET user_id = ?1 WHERE trip_id = ?2 AND user_id = ?3 AND role = 'OWNER'")
					.setParameter(1, newOwner).setParameter(2, tripId).setParameter(3, session)
					.executeUpdate();

			entityManager.createNativeQuery(
					"UPDATE preference_snapshot SET user_id = ?1 WHERE trip_id = ?2 AND user_id = ?3")
					.setParameter(1, newOwner).setParameter(2, tripId).setParameter(3, session)
					.executeUpdate();

			entityManager.createNativeQuery(
					"UPDATE constraint_snapshot SET user_id = ?1 WHERE trip_id = ?2 AND user_id = ?3")
					.setParameter(1, newOwner).setParameter(2, tripId).setParameter(3, session)
					.executeUpdate();
		}

		return anonymousTrips.size();
	}

	private static TripJpaEntity toEntity(Trip t) {
		return new TripJpaEntity(
				UUID.fromString(t.tripId()), UUID.fromString(t.createdBy()), t.ownerType().name(),
				t.startDate(), t.finishDate(),
				t.originLat(), t.originLng(),
				t.budgetKrw() == null ? null : t.budgetKrw().longValue(),
				t.partySize(), t.timeWindow(), t.timezone(),
				t.travelModes(), t.timeWindowStart(), t.timeWindowEnd(),
				t.accommodationPlaceId() == null ? null : UUID.fromString(t.accommodationPlaceId()),
				t.englishMenuRequired(), t.foreignCardRequired(), t.soloFriendlyPriority(),
				t.maxTransitTransfers(), t.status(),
				toOffset(t.createdAt()), toOffset(t.updatedAt()), toOffset(t.deletedAt()));
	}

	private static Trip toDomain(TripJpaEntity e) {
		return Trip.builder()
				.tripId(e.tripId().toString())
				.createdBy(e.ownerUserId().toString())
				.ownerType(Trip.OwnerType.valueOf(e.ownerType()))
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
				.accommodationPlaceId(e.accommodationPlaceId() == null ? null : e.accommodationPlaceId().toString())
				.englishMenuRequired(e.englishMenuRequired())
				.foreignCardRequired(e.foreignCardRequired())
				.soloFriendlyPriority(e.soloFriendlyPriority())
				.maxTransitTransfers(e.maxTransitTransfers())
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

	/** S15P21E201-299 — 초대 흔적 세 칸까지 함께 되살린다. 번역은 {@link JpaTripMembershipRepository#toDomain} 한 곳에 둔다. */
	private static TripMember toDomain(TripMemberJpaEntity e) {
		return JpaTripMembershipRepository.toDomain(e);
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
		// 🔴 S15P21E201 사용자 리포트 — value 를 항상 null 로 읽어 왔다. WHEELCHAIR·STROLLER·
		// HEAVY_LUGGAGE·STAIRS_AVOIDANCE 는 threshold(미터)가 아니라 value("true")로 답을
		// 싣는데, 여기서 value 를 버리고 threshold 만 복원하다 보니 answerStatus=SELECTED인데
		// value·threshold 가 둘 다 null인 TripConstraint 가 만들어져 생성자가 거부했다
		// (recommendation-jobs 요청이 400 RECOMMENDATION_JOB_VALIDATION_FAILED로 실패 —
		// trip 생성 자체는 원본 값을 그대로 써서 통과하므로 이 read 경로에서만 재현된다).
		return new TripConstraint(
				e.constraintAnswerId().toString(), tripId, e.constraintType(), e.constraintKey(),
				e.hard() ? TripConstraint.Severity.HARD : TripConstraint.Severity.SOFT,
				defaultOperatorFor(e.constraintType()),
				extractValue(e.valueJson()), extractMeters(e.valueJson()),
				TripConstraint.EvidenceStatus.NEEDS_REVIEW,
				e.answerStatus(), PersonalizationScope.TRIP, e.dietRequirement());
	}

	/**
	 * {@link #valueJsonOf} 가 문자열 값에 씌운 JSON 문자열 인코딩({@code "\"escaped\""})을
	 * 되돌린다. meters 객체({@code {"meters":N}})는 여기서 다루지 않는다 — 그건 threshold
	 * 쪽({@link #extractMeters})의 몫이라 둘 다 값을 낼 일이 없다(하나가 채워지면 나머지는
	 * null).
	 */
	private static String extractValue(String valueJson) {
		if (valueJson == null || valueJson.length() < 2
				|| valueJson.charAt(0) != '"' || valueJson.charAt(valueJson.length() - 1) != '"') {
			return null;
		}
		String inner = valueJson.substring(1, valueJson.length() - 1);
		return inner.replace("\\\"", "\"").replace("\\\\", "\\");
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
