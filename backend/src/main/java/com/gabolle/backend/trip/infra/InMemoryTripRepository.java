package com.gabolle.backend.trip.infra;

import com.gabolle.backend.trip.domain.PreferenceSnapshot;
import com.gabolle.backend.trip.domain.Trip;
import com.gabolle.backend.trip.domain.TripConstraint;
import com.gabolle.backend.trip.domain.TripMember;
import com.gabolle.backend.trip.domain.TripRepository;
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

    private static String keyOf(String userId, String key) {
        return userId + "#" + key;
    }

    /** 시연·테스트용. */
    public long tripCount() {
        return trips.size();
    }
}
