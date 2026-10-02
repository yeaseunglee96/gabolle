package com.gabolle.backend.trip.infra;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
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
import com.gabolle.backend.trip.domain.UserPreferenceDefaultsSaved;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * 여행 저장소의 PostgreSQL 구현.
 *
 * <p>evidenceStatus·operator 는 constraint_answer 표에 칸이 없어 왕복하지 않는다. 읽어올 때
 * evidenceStatus 는 NEEDS_REVIEW 로, operator 는 종류별 관례값(MOBILITY 는 LTE, 나머지는
 * EXCLUDES)으로 채운다. 멱등 키는 SAVEPOINT 대신 ON CONFLICT DO NOTHING 을 쓴다 — 이 환경의
 * 트랜잭션 매니저에서 PROPAGATION_NESTED 가 실제로 안 먹힌다.
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
	private final ApplicationEventPublisher events;

	@PersistenceContext
	private EntityManager entityManager;

	public JpaTripRepository(TripJpaRepository tripJpaRepository,
			TripMemberJpaRepository memberJpaRepository,
			PreferenceSnapshotJpaRepository preferenceSnapshotJpaRepository,
			PreferenceAnswerJpaRepository preferenceAnswerJpaRepository,
			ConstraintSnapshotJpaRepository constraintSnapshotJpaRepository,
			ConstraintAnswerJpaRepository constraintAnswerJpaRepository,
			ApplicationEventPublisher events) {
		this.tripJpaRepository = tripJpaRepository;
		this.memberJpaRepository = memberJpaRepository;
		this.preferenceSnapshotJpaRepository = preferenceSnapshotJpaRepository;
		this.preferenceAnswerJpaRepository = preferenceAnswerJpaRepository;
		this.constraintSnapshotJpaRepository = constraintSnapshotJpaRepository;
		this.constraintAnswerJpaRepository = constraintAnswerJpaRepository;
		this.events = events;
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
	 * 저장된 행을 읽어 지운 시각만 찍는다. {@code toEntity(trip)} 로 통째로 덮어쓰지 않는다 —
	 * 이 클래스는 {@code version}·{@code origin_source}·{@code origin_area_code} 를 일부러
	 * 매핑하지 않아서, 덮어쓰면 누가 그 칸을 매핑하는 순간 조용히 값이 날아간다.
	 *
	 * <p>없는 여행이면 {@link IllegalStateException} 이다 — 삭제 경로는 회원 여부까지 판정한
	 * 뒤에 오므로 못 찾는 것은 정상 흐름이 아니다.
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
	 * 상태 칸만 옮긴다. {@link #softDelete} 와 같은 이유로 읽어서 고치지, 통째로 덮어쓰지 않는다.
	 *
	 * <p>{@code @Transactional} 을 일부러 안 붙인다 — 이 자리는 일정이 저장되는 바깥 트랜잭션
	 * 안에서 불리고, 여기서 새 트랜잭션을 열면 일정 저장이 실패해도 상태만 READY 로 남는다.
	 */
	@Override
	public void updateStatus(Trip trip) {
		TripJpaEntity entity = tripJpaRepository.findById(UUID.fromString(trip.tripId()))
				.orElseThrow(() -> new IllegalStateException("상태를 바꾸려는 여행이 표에 없다: tripId=" + trip.tripId()));
		entity.changeStatus(trip.status(), toOffset(trip.updatedAt()));
		tripJpaRepository.save(entity);
	}

	/**
	 * 이름 칸만 저장한다. 통째로 덮어쓰면 그 사이 다른 경로가 바꾼 칸(상태·삭제 시각)까지
	 * 옛 값으로 되돌린다.
	 */
	@Override
	public void updateTitle(Trip trip) {
		TripJpaEntity entity = tripJpaRepository.findById(UUID.fromString(trip.tripId()))
				.orElseThrow(() -> new IllegalStateException("이름을 바꾸려는 여행이 표에 없다: tripId=" + trip.tripId()));
		entity.changeTitle(trip.title(), toOffset(trip.updatedAt()));
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
	 * 질의는 여행 수와 무관하게 둘이다 — 참여 행에서 식별자와 역할을 얻고, 그 묶음으로 여행을
	 * 한 번 읽는다. 여행마다 {@code findById} 를 부르면 N+1 이 된다.
	 *
	 * <p>순서는 질의의 {@code updated_at} 내림차순 그대로다. 역할은 맵에서 찾아 붙이기만 하고
	 * 여기서 다시 정렬하지 않는다.
	 */
	@Override
	@Transactional(readOnly = true)
	public List<TripRepository.MemberTrip> findTripsForMember(String userId, int limit) {
		if (limit <= 0) {
			return List.of();
		}

		Map<UUID, TripMember.Role> roleByTripId = memberJpaRepository.findByUserId(UUID.fromString(userId)).stream()
				.collect(Collectors.toMap(TripMemberJpaEntity::tripId, TripMemberJpaEntity::role,
						// uq_trip_member (trip_id, user_id) 가 한 사람당 한 행을 보장하므로 충돌은
						// 안 난다. 그래도 비워 두지 않는다 — 제약이 사라진 날 예외로 죽는 것보다 낫다.
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
		// 마지막 판 + 1. 동시에 두 번 저장하면 uq_preference_snapshot_user 가 하나를 거부하고,
		// 그 거부가 곧 직렬화다 — 락을 따로 걸지 않는 이유.
		int nextVersion = preferenceSnapshotJpaRepository
				.findTopByUserIdAndTripIdIsNullOrderByVersionDesc(ownerUserId)
				.map(e -> e.version() + 1)
				.orElse(1);

		UUID snapshotId = UUID.randomUUID();
		// trip_id 는 반드시 null 이어야 한다. ck_preference_snapshot_scope_trip 이
		// (scope='TRIP') = (trip_id IS NOT NULL) 을 요구한다.
		preferenceSnapshotJpaRepository.save(new PreferenceSnapshotJpaEntity(
				snapshotId, ownerUserId, null, nextVersion, PersonalizationScope.USER, toOffset(at)));
		for (PreferenceSnapshot.PreferenceAnswer answer : answers) {
			preferenceAnswerJpaRepository.save(new PreferenceAnswerJpaEntity(
					UUID.randomUUID(), snapshotId, answer.dimension(), answer.valueJson(),
					answer.status(), toOffset(at)));
		}
		// 취향 판을 새로 접게 알린다 (S15P21E201-1515). 여기서 내는 이유는 계정 기본 취향을 쓰는
		// 문이 이 메서드 하나라서다 — 설문(PreferenceDefaultsService)도 씀씀이(SpendProfileService)도
		// 여기를 지난다. 받는 쪽은 커밋 뒤에 돈다. 커밋 전에 접으면 롤백된 설문으로 판을 만든다.
		this.events.publishEvent(new UserPreferenceDefaultsSaved(ownerUserId));
		return new PreferenceSnapshot(snapshotId.toString(), null, nextVersion, answers,
				PersonalizationScope.USER, List.of(), at);
	}

	@Override
	public Optional<PreferenceSnapshot> findSnapshotById(String preferenceSnapshotId) {
		// 추천 Job 이 기록해 둔 그 판을 직접 읽는다. findLatestSnapshot 을 쓰면 Job 이 돌기 전에
		// 사용자가 취향을 다시 답했을 때 "그때 그 판" 이 아니라 "지금 최신 판" 을 읽게 된다.
		return preferenceSnapshotJpaRepository.findById(UUID.fromString(preferenceSnapshotId))
				.map(this::toDomain);
	}

	@Override
	public List<TripConstraint> findConstraintsBySnapshotId(String constraintSnapshotId) {
		// findConstraints(tripId) 를 재사용하지 않는다 — 그쪽은 첫 번째 스냅샷을 집는데,
		// 추천 Job 이 기록해 둔 스냅샷과 다를 수 있다.
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

		// 🔴 키가 묶인 여행이 이미 지워졌으면(S15P21E201-1716) 재시도로 돌려주지 않는다.
		//    프론트는 여행 조건(출발지·날짜·인원)으로 늘 같은 키를 만든다. 같은 조건으로 여행을 지웠다 다시 만들면 이 키가
		//    지운 여행을 가리킨 채 남아 있고, 그 여행을 돌려주면 추천 요청(POST /trips/{id}/recommendation-jobs)이 404 로
		//    끝난다 — 「지금은 일정을 만들 수 없어요」. 지운 여행은 사용자에게 없는 여행이므로 이 요청은 새 여행이다.
		//
		//    갈아 묶기는 「아직 그 지운 여행에 묶여 있을 때만」({@code trip_id = ?5}) 바꾼다. 같은 키로 동시에 온 요청 둘이
		//    모두 지운 여행을 보았을 때 한쪽만 이기게 하는 장치다(한 행에 대한 갱신은 한 번에 하나만 통과한다).
		if (existingTrip.isDeleted()) {
			int rebound = entityManager.createNativeQuery(
					"UPDATE trip_idempotency SET trip_id = ?3, created_at = ?4 "
							+ "WHERE user_id = ?1 AND idempotency_key = ?2 AND trip_id = ?5")
					.setParameter(1, UUID.fromString(userId))
					.setParameter(2, idempotencyKey)
					.setParameter(3, UUID.fromString(trip.tripId()))
					.setParameter(4, toOffset(trip.createdAt()))
					.setParameter(5, existingTripId)
					.executeUpdate();

			if (rebound == 1) {
				save(trip, tripConstraints, owner, snapshot);
				return new SaveOutcome(trip, snapshot, true);
			}

			// 동시에 다른 요청이 먼저 갈아 묶었다 — 그 요청이 만든 여행을 돌려준다(이쪽 요청은 재시도가 된다).
			UUID reboundTripId = (UUID) entityManager.createNativeQuery(
					"SELECT trip_id FROM trip_idempotency WHERE user_id = ?1 AND idempotency_key = ?2")
					.setParameter(1, UUID.fromString(userId))
					.setParameter(2, idempotencyKey)
					.getSingleResult();
			Trip winner = findById(reboundTripId.toString()).orElseThrow();
			return new SaveOutcome(winner, findLatestSnapshot(reboundTripId.toString()).orElse(null), false);
		}

		PreferenceSnapshot existingSnapshot = findLatestSnapshot(existingTripId.toString()).orElse(null);
		return new SaveOutcome(existingTrip, existingSnapshot, false);
	}

	/**
	 * 네이티브 SQL 로만 옮긴다. {@code trip.owner_user_id}·{@code trip_member.user_id} 는 둘 다
	 * {@code updatable = false} 라, 엔티티를 고쳐 저장하는 평소 경로로는 조용히 안 바뀐다.
	 *
	 * <p>여행마다 trip → trip_member(OWNER 행) → preference_snapshot → constraint_snapshot 순으로
	 * 옮긴다. 뒤의 두 스냅샷 표는 도메인에 없는 인프라 칸이라 여기서 빠뜨리면 승계된 여행의
	 * 취향·제약이 사라진 익명 세션 UUID 를 계속 가리킨다.
	 *
	 * <p>비회원도 일정을 만들고 고치므로 일정 판·제외 목록·추천 판단·추천 작업의 요청자 칸도 함께
	 * 옮긴다. 비회원에게 새 자리를 열면서 그 자리가 세션 ID 를 적는 표가 생기면 여기에 더한다.
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

			// 아래 셋은 일정·추천 쪽 표다. 여행이 그 표를 모르게 두는 것이 원칙이지만, 승계는 한
			// 트랜잭션에서 끝나야 해서 여기 모은다. 안 옮기면 일정 이력의 「내가 고친 것」과 추천
			// 판단의 「누가 정했나」가 사라진 세션 ID 를 가리킨다.
			entityManager.createNativeQuery("""
					UPDATE itinerary_versions SET created_by = ?1
					WHERE created_by = ?3 AND itinerary_id IN (SELECT itinerary_id FROM itineraries WHERE trip_id = ?2)
					""")
					.setParameter(1, newOwner).setParameter(2, tripId).setParameter(3, session)
					.executeUpdate();

			entityManager.createNativeQuery("""
					UPDATE itinerary_excluded_place SET excluded_by = ?1
					WHERE excluded_by = ?3 AND itinerary_version_id IN (
						SELECT v.itinerary_version_id FROM itinerary_versions v
						JOIN itineraries i ON i.itinerary_id = v.itinerary_id WHERE i.trip_id = ?2)
					""")
					.setParameter(1, newOwner).setParameter(2, tripId).setParameter(3, session)
					.executeUpdate();

			entityManager.createNativeQuery(
					"UPDATE recommendation_place_action SET decided_by_user_id = ?1 WHERE trip_id = ?2 AND decided_by_user_id = ?3")
					.setParameter(1, newOwner).setParameter(2, tripId).setParameter(3, session)
					.executeUpdate();
		}

		// 추천 작업의 주인. 작업 조회·진행률·결과가 이 칸으로 주인을 가리므로(RecommendationJobController#isOwner)
		// 안 옮기면 로그인한 순간 자기 일정 생성 화면이 404 가 된다. 아직 도는 작업도 같이 옮겨진다 —
		// 작업기는 끝날 때 이 칸을 다시 쓰지 않는다.
		entityManager.createNativeQuery("UPDATE recommendation_job SET user_id = ?1 WHERE user_id = ?2")
				.setParameter(1, newOwner).setParameter(2, session)
				.executeUpdate();

		// 재시도 키는 옮기지 않고 지운다. (사람, 키) 가 기본키라 옮기면 회원 쪽 키와 부딪칠 수 있고,
		// 로그인한 뒤의 재시도는 새 신원으로 오므로 세션의 키가 다시 쓰일 일이 없다.
		entityManager.createNativeQuery("DELETE FROM trip_idempotency WHERE user_id = ?1")
				.setParameter(1, session)
				.executeUpdate();
		entityManager.createNativeQuery("DELETE FROM recommendation_job_idempotency WHERE user_id = ?1")
				.setParameter(1, session)
				.executeUpdate();

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
				t.maxTransitTransfers(), t.pace(), t.title(), t.status(),
				toOffset(t.createdAt()), toOffset(t.updatedAt()), toOffset(t.deletedAt()),
				t.accommodationArea());
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
				.pace(e.pace())
				.accommodationPlaceId(e.accommodationPlaceId() == null ? null : e.accommodationPlaceId().toString())
				.accommodationArea(e.accommodationArea())
				.englishMenuRequired(e.englishMenuRequired())
				.foreignCardRequired(e.foreignCardRequired())
				.soloFriendlyPriority(e.soloFriendlyPriority())
				.maxTransitTransfers(e.maxTransitTransfers())
				.timezone(e.timezone())
				.title(e.title())
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

	/** 초대 흔적 세 칸까지 함께 되살린다. 번역은 {@link JpaTripMembershipRepository#toDomain} 한 곳에 둔다. */
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
		// value 와 threshold 를 둘 다 복원해야 한다. WHEELCHAIR·STROLLER·HEAVY_LUGGAGE·
		// STAIRS_AVOIDANCE 는 threshold(미터)가 아니라 value("true")로 답을 싣기 때문에,
		// 한쪽만 복원하면 answerStatus=SELECTED 인데 값이 둘 다 null 이라 생성자가 거부한다.
		return new TripConstraint(
				e.constraintAnswerId().toString(), tripId, e.constraintType(), e.constraintKey(),
				e.hard() ? TripConstraint.Severity.HARD : TripConstraint.Severity.SOFT,
				defaultOperatorFor(e.constraintType()),
				extractValue(e.valueJson()), extractMeters(e.valueJson()),
				TripConstraint.EvidenceStatus.NEEDS_REVIEW,
				e.answerStatus(), PersonalizationScope.TRIP, e.dietRequirement());
	}

	/**
	 * {@link #valueJsonOf} 가 문자열 값에 씌운 JSON 인코딩을 되돌린다. meters 객체는
	 * {@link #extractMeters} 의 몫이라 여기서 다루지 않는다 — 하나가 채워지면 나머지는 null 이다.
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
