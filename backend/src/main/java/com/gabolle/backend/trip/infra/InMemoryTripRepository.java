package com.gabolle.backend.trip.infra;

import com.gabolle.backend.trip.domain.PreferenceSnapshot;
import com.gabolle.backend.trip.domain.Trip;
import com.gabolle.backend.trip.domain.TripConstraint;
import com.gabolle.backend.trip.domain.TripMember;
import com.gabolle.backend.trip.domain.PersonalizationScope;
import com.gabolle.backend.trip.domain.TripRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/**
 * 메모리 여행 저장소 — {@code no-db} 전용.
 *
 * <p>🔴 S15P21E201-461 이 {@code trip} 표를 {@link JpaTripRepository} 로 옮기면서
 * {@code db}·{@code dev} 프로필에는 이 빈을 만들지 않는다 — 인증·이벤트가 이미 쓰는
 * 방식과 같다({@code @Profile({"db","dev"})}). 둘 다 살아 있으면 {@code TripRepository}
 * 빈이 둘이 되어 애플리케이션이 못 뜬다.
 *
 * <p>🔴 서버를 끄면 사라진다. DB 없이 컴파일·헬스 확인만 하는 {@code no-db} 프로필
 * 전용이다.
 */
@Repository
@Profile("!db & !dev")
public class InMemoryTripRepository implements TripRepository {

    private final Map<String, Trip> trips = new ConcurrentHashMap<>();
    private final Map<String, List<TripConstraint>> constraints = new ConcurrentHashMap<>();
    private final Map<String, List<TripMember>> members = new ConcurrentHashMap<>();
    private final Map<String, List<PreferenceSnapshot>> snapshots = new ConcurrentHashMap<>();

    /**
     * 계정 기본 취향 — 열쇠가 사용자다 (S15P21E201-547).
     *
     * <p>🔴 {@link #snapshots}(열쇠가 여행)와 <b>섞지 않는다.</b> 한 통에 담으면
     * {@code findLatestSnapshot(tripId)} 가 계정 기본값을 여행 스냅샷으로 집을 수 있고,
     * 그것이 이 티켓이 막으려는 바로 그 혼동이다. DB 쪽도 같은 표에 두면서
     * {@code trip_id IS NULL} 조건으로 갈라 놓았다.
     */
    private final Map<String, List<PreferenceSnapshot>> userDefaults = new ConcurrentHashMap<>();

    /** 열쇠는 "사용자#키". 멱등 키는 사용자마다 따로다 — 남의 키와 겹쳐도 안 된다. */
    private final Map<String, Binding> idempotency = new ConcurrentHashMap<>();

    private record Binding(String fingerprint, String tripId) {}

    @Override
    public Trip save(Trip trip, List<TripConstraint> tripConstraints,
                     TripMember owner, PreferenceSnapshot snapshot) {
        trips.put(trip.tripId(), trip);
        constraints.put(trip.tripId(), List.copyOf(tripConstraints));
        members.put(trip.tripId(), new ArrayList<>(List.of(owner)));
        snapshots.put(trip.tripId(), new ArrayList<>(List.of(snapshot)));
        return trip;
    }

    @Override
    public Optional<Trip> findById(String tripId) {
        return Optional.ofNullable(trips.get(tripId));
    }

    /**
     * S15P21E201-746 — JPA 판과 같은 규칙. 지운 시각이 찍힌 여행으로 바꿔 넣기만 한다.
     *
     * <p>여기 담긴 것은 도메인 객체 자체라 {@code markDeleted} 가 이미 그 객체를 바꿔
     * 놓았을 수 있다. 그래도 다시 넣는 이유는 <b>저장이 일어나야 남는다</b> 는 규칙을
     * 두 구현이 같게 지키기 위해서다 — 이 판만 저장 없이도 남으면, 저장을 빠뜨린 코드가
     * 여기서는 통과하고 실제 DB 에서만 깨진다.
     */
    @Override
    public void softDelete(Trip trip) {
        trips.put(trip.tripId(), trip);
    }

    /**
     * S15P21E201-964 — 위 {@link #softDelete} 와 같은 이유로 저장을 한 번 거친다.
     * 상태를 바꾸는 규칙은 도메인이 이미 태웠으므로 여기서는 넣기만 한다.
     */
    @Override
    public void updateStatus(Trip trip) {
        trips.put(trip.tripId(), trip);
    }

    /** S15P21E201-1023 — 위 {@link #updateStatus} 와 같은 이유로 저장을 한 번 거친다. */
    @Override
    public void updateTitle(Trip trip) {
        trips.put(trip.tripId(), trip);
    }

    @Override
    public List<TripConstraint> findConstraints(String tripId) {
        return constraints.getOrDefault(tripId, List.of());
    }

    @Override
    public List<TripMember> findMembers(String tripId) {
        return members.getOrDefault(tripId, List.of());
    }

    /**
     * S15P21E201-738 — JPA 판과 같은 규칙으로 고른다. 참여 표를 훑어 내가 들어 있는
     * 여행을 모으고, 지운 여행을 빼고, 최근에 손댄 순으로 상한까지 자른다.
     */
    @Override
    public List<TripRepository.MemberTrip> findTripsForMember(String userId, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        return members.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream()
                        .filter(member -> member.userId().equals(userId))
                        .map(member -> new TripRepository.MemberTrip(trips.get(entry.getKey()), member.role())))
                .filter(row -> row.trip() != null && row.trip().deletedAt() == null)
                .sorted(Comparator.comparing((TripRepository.MemberTrip row) -> row.trip().updatedAt(),
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .toList();
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

    @Override
    public Optional<PreferenceSnapshot> findUserDefaults(String userId) {
        return userDefaults.getOrDefault(userId, List.of()).stream()
                .max(Comparator.comparingInt(PreferenceSnapshot::version));
    }

    @Override
    public PreferenceSnapshot saveUserDefaults(String userId,
                                               List<PreferenceSnapshot.PreferenceAnswer> answers,
                                               Instant at) {
        List<PreferenceSnapshot> history = userDefaults.computeIfAbsent(userId, k -> new ArrayList<>());
        synchronized (history) {
            int nextVersion = history.stream()
                    .mapToInt(PreferenceSnapshot::version)
                    .max()
                    .orElse(0) + 1;
            // 🔴 tripId 는 null 이다 — 계정 기본값에는 여행이 없다.
            PreferenceSnapshot saved = new PreferenceSnapshot(
                    java.util.UUID.randomUUID().toString(), null, nextVersion,
                    answers, PersonalizationScope.USER, List.of(), at);
            history.add(saved);
            return saved;
        }
    }

    @Override
    public Optional<PreferenceSnapshot> findSnapshotById(String preferenceSnapshotId) {
        // 🔴 tripId 를 모르는 채로 snapshotId 만 받으므로 전체를 뒤진다 — no-db 프로필은
        // 여행 몇 건짜리 데모용이라 성능이 문제되지 않는다.
        return snapshots.values().stream()
                .flatMap(List::stream)
                .filter(s -> preferenceSnapshotId.equals(s.snapshotId()))
                .findFirst();
    }

    /**
     * 🔴 이 프로필({@code no-db})에는 추천 기능 자체가 안 붙는다 — {@link #findLatestConstraintSnapshotId}
     * 와 같은 이유로 constraint_snapshot 개념을 만들지 않으므로 항상 비어 있다.
     */
    @Override
    public List<TripConstraint> findConstraintsBySnapshotId(String constraintSnapshotId) {
        return List.of();
    }

    /**
     * 🔴 이 프로필({@code no-db})에는 추천 기능 자체가 안 붙는다({@code RecommendationService}
     * 도 {@code @Profile({"db","dev"})} 다) — 그래서 진짜 constraint_snapshot 개념을
     * 만들지 않는다. 항상 비어 있다.
     */
    @Override
    public Optional<String> findLatestConstraintSnapshotId(String tripId) {
        return Optional.empty();
    }

    /**
     * 🔴 키 확보와 저장을 <b>한 동작</b>으로 한다.
     *
     * <p>{@code compute} 는 같은 열쇠에 대해 <b>한 번에 하나의 스레드만</b> 들여보낸다.
     * 그래서 확보와 저장 사이에 다른 요청이 끼어들 수 없다.
     * DB 에서는 트랜잭션 + {@code UNIQUE (user_id, idempotency_key)} 가 이 자리다.
     */
    @Override
    public SaveOutcome saveWithIdempotency(String userId, String idempotencyKey, String fingerprint,
                                           Trip trip, List<TripConstraint> tripConstraints,
                                           TripMember owner, PreferenceSnapshot snapshot) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            save(trip, tripConstraints, owner, snapshot);
            return new SaveOutcome(trip, snapshot, true);
        }

        String[] winner = new String[1];

        idempotency.compute(keyOf(userId, idempotencyKey), (k, prior) -> {
            if (prior != null) {
                if (!prior.fingerprint().equals(fingerprint)) {
                    // API-09 — 같은 키를 다른 본문으로 재사용하면 409 다.
                    throw new IdempotencyKeyConflictException(idempotencyKey);
                }
                winner[0] = prior.tripId();          // 재시도 — 기존 것을 돌려준다
                return prior;
            }
            // 🔴 이긴 쪽만 여기 들어온다. 저장까지 이 안에서 끝낸다.
            save(trip, tripConstraints, owner, snapshot);
            winner[0] = trip.tripId();
            return new Binding(fingerprint, trip.tripId());
        });

        boolean created = trip.tripId().equals(winner[0]);
        if (created) {
            return new SaveOutcome(trip, snapshot, true);
        }
        Trip existing = trips.get(winner[0]);
        return new SaveOutcome(existing, findLatestSnapshot(winner[0]).orElse(null), false);
    }

    /**
     * S15P21E201-317 — JPA 판과 같은 규칙. {@code createdBy} 는 불변이라({@link Trip} 필드가
     * final) 승계된 새 값으로 {@link Trip.Builder} 를 다시 태워 바꿔 넣는다. preference_snapshot·
     * constraint_snapshot 은 이 프로필({@code no-db})에 개념 자체가 없으므로(클래스 상단
     * 주석) 건드릴 것이 없다.
     */
    @Override
    public int claimAnonymousTrips(String sessionId, String newOwnerId, Instant at) {
        List<Trip> anonymousTrips = trips.values().stream()
                .filter(t -> t.ownerType() == Trip.OwnerType.ANONYMOUS && t.createdBy().equals(sessionId))
                .toList();

        for (Trip trip : anonymousTrips) {
            Trip claimed = Trip.builder()
                    .tripId(trip.tripId())
                    .createdBy(newOwnerId)
                    .ownerType(Trip.OwnerType.USER)
                    .startDate(trip.startDate())
                    .finishDate(trip.finishDate())
                    .originLat(trip.originLat())
                    .originLng(trip.originLng())
                    .budgetKrw(trip.budgetKrw())
                    .partySize(trip.partySize())
                    .timeWindow(trip.timeWindow())
                    .timeWindowStart(trip.timeWindowStart())
                    .timeWindowEnd(trip.timeWindowEnd())
                    .travelModes(trip.travelModes())
                    .timezone(trip.timezone())
                    .status(trip.status())
                    .createdAt(trip.createdAt())
                    .updatedAt(at)
                    .deletedAt(trip.deletedAt())
                    .build();
            trips.put(trip.tripId(), claimed);

            List<TripMember> tripMembers = members.get(trip.tripId());
            if (tripMembers != null) {
                for (int i = 0; i < tripMembers.size(); i++) {
                    TripMember m = tripMembers.get(i);
                    if (m.role() == TripMember.Role.OWNER && m.userId().equals(sessionId)) {
                        tripMembers.set(i, m.claimedBy(newOwnerId));
                    }
                }
            }
        }

        return anonymousTrips.size();
    }

    private static String keyOf(String userId, String key) {
        return userId + "#" + key;
    }

    /** 시연·테스트용. */
    public long tripCount() {
        return trips.size();
    }
}
