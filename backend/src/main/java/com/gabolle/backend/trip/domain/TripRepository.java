package com.gabolle.backend.trip.domain;

import java.util.List;
import java.util.Optional;

/**
 * 여행 저장소 — 인터페이스만 도메인에 둔다. 구현은 {@code infra} 에 있다.
 *
 * <p>🔴 이 파일은 JPA·Spring 을 import 하지 않는다. 그래서 도메인 규칙을
 * DB 없이 테스트할 수 있다.
 */
public interface TripRepository {

    /**
     * 여행과 그에 딸린 것을 <b>한 번에</b> 저장한다.
     *
     * <p>🔴 나눠서 저장하면 "여행은 생겼는데 소유자가 없는" 상태가 생긴다.
     * 그러면 만든 사람이 자기 여행을 못 본다.
     */
    Trip save(Trip trip, List<TripConstraint> constraints,
              TripMember owner, PreferenceSnapshot snapshot);

    Optional<Trip> findById(String tripId);

    List<TripConstraint> findConstraints(String tripId);

    List<TripMember> findMembers(String tripId);

    /** 특정 판. 없으면 비어 있다. */
    Optional<PreferenceSnapshot> findSnapshot(String tripId, int version);

    /**
     * 계정 기본 취향({@code scope=USER})의 최신 판 (S15P21E201-547).
     *
     * <p>🔴 <b>여행 스냅샷과 키가 다르다.</b> 여행 스냅샷은 {@code trip_id} 로 찾고 이것은
     * {@code user_id} 로 찾는다. 스키마가 그렇게 나뉘어 있다 — {@code scope='USER'} 행은
     * {@code trip_id} 가 NULL 이고, 판 번호의 유일성도 {@code uq_preference_snapshot_user}
     * ({@code (user_id, version) WHERE trip_id IS NULL})가 따로 본다.
     *
     * <p>이 조회가 없어서 <b>계정 기본 취향이 저장되지도 읽히지도 않고 있었다.</b> 같은
     * 사람이 두 번째 여행을 만들면 알레르기부터 다시 물어야 했다.
     */
    Optional<PreferenceSnapshot> findUserDefaults(String userId);

    /**
     * 계정 기본 취향을 <b>새 판으로</b> 저장한다 (S15P21E201-547).
     *
     * <p>🔴 기존 판을 고치지 않는다. {@link PreferenceSnapshot} 이 불변인 것과 같은
     * 이유다 — 이미 만들어진 여행의 스냅샷이 "그때의 계정 기본값을 복사한 것" 이라고
     * 주장하려면 그때의 판이 남아 있어야 한다.
     *
     * <p>🔴 판 번호는 <b>구현이 정한다</b>(그 사용자의 마지막 판 + 1). 부르는 쪽이 정하게
     * 하면 동시에 두 번 저장할 때 같은 번호가 나오고, 그 충돌은
     * {@code uq_preference_snapshot_user} 에서 JDBC 안쪽 예외로 터져 어느 요청이
     * 문제였는지를 알려주지 못한다.
     *
     * @param answers 차원별 답. 이 목록이 그 판의 전부다 — 부분 갱신이 아니다
     * @return 저장된 판(판 번호와 스냅샷 ID 가 채워져 있다)
     */
    PreferenceSnapshot saveUserDefaults(String userId,
                                        List<PreferenceSnapshot.PreferenceAnswer> answers,
                                        java.time.Instant at);

    /** 가장 최신 판. */
    Optional<PreferenceSnapshot> findLatestSnapshot(String tripId);

    /**
     * 스냅샷 ID 로 <b>그 판</b>을 직접 읽는다 (S15P21E201-604 — 추천 엔진 전용).
     *
     * <p>🔴 {@link #findConstraints(String)}·{@link #findLatestSnapshot(String)} 를 재사용하면
     * 안 된다. 추천 Job 은 시작할 때 스냅샷 ID 를 <b>기록해 두고</b>, 실행은 나중에(비동기) 될 수
     * 있다. 그 사이 사용자가 취향을 다시 답하면 "최신 판" 은 바뀌지만 Job 이 기록해 둔 판은
     * 그대로다 — "이 버전으로 만든 결과" 라는 주장이 거짓이 되지 않으려면 반드시 기록된 그 ID로
     * 읽어야 한다.
     */
    Optional<PreferenceSnapshot> findSnapshotById(String preferenceSnapshotId);

    /**
     * 제약 스냅샷 ID 로 <b>그 판</b>의 제약 전부를 직접 읽는다 (S15P21E201-604 — 추천 엔진 전용).
     *
     * <p>🔴 {@link #findConstraints(String)} 를 재사용하면 안 된다. {@code JpaTripRepository}
     * 구현이 {@code findByTripId(id).get(0)} 로 <b>첫 번째</b> 스냅샷을 집는데, 추천 Job 이
     * 기록해 둔 스냅샷과 다를 수 있다.
     */
    List<TripConstraint> findConstraintsBySnapshotId(String constraintSnapshotId);

    /**
     * 가장 최신 제약 스냅샷의 식별자 — S15P21E201-192 가 추천 Job 을 만들 때 쓴다.
     *
     * <p>🔴 제약을 하나도 안 답한 여행은 이 값이 없다({@code save} 가 제약이 비어 있으면
     * constraint_snapshot 행 자체를 안 만들기 때문이다). 그런 여행은 추천을 요청할 수
     * 없다 — {@link com.gabolle.backend.recommendation.application.RecommendationCommand}
     * 가 이 값을 필수로 요구한다.
     */
    Optional<String> findLatestConstraintSnapshotId(String tripId);

    /**
     * 🔴 멱등 키를 확보하면서 저장한다 — <b>한 동작이어야 한다</b> (API-09).
     *
     * <p>처음에는 "키 조회 → 저장 → 키 묶기" 세 단계로 나눴는데 <b>테스트가 잡았다.</b>
     * 같은 키로 8개가 동시에 오면 전부 조회에서 "없음" 을 받고, 전부 여행을 만들고,
     * 마지막에 하나만 묶였다. 여행은 8개가 남았다.
     *
     * <pre>
     * 잘못된 순서              올바른 순서
     * ① 키 조회 (8개 통과)      ① 키 확보를 시도한다 (1개만 성공)
     * ② 저장   (🔴 8개 생성)    ② 이긴 쪽만 저장한다
     * ③ 키 묶기 (1개만)         ③ 진 쪽은 이긴 쪽의 여행을 돌려받는다
     * </pre>
     *
     * <p>🔴 <b>원자적 보호는 지키려는 대상을 감싸야 한다.</b> 뒤에 두면 이미 만들어진 뒤다.
     *
     * <p>DB 구현에서는 하나의 트랜잭션 안에서 {@code INSERT INTO trip_idempotency}
     * 가 {@code UNIQUE (user_id, idempotency_key)} 에 걸리는 것이 이 자리를 대신한다.
     *
     * @param idempotencyKey {@code null} 이면 중복 방지 없이 그냥 저장한다
     * @return 새로 저장했으면 {@code created=true}, 이미 있었으면 그 여행과 {@code false}
     * @throws IdempotencyKeyConflictException 같은 키가 <b>다른 본문</b>으로 이미 쓰였을 때
     */
    SaveOutcome saveWithIdempotency(String userId, String idempotencyKey, String requestFingerprint,
                                    Trip trip, List<TripConstraint> constraints,
                                    TripMember owner, PreferenceSnapshot snapshot);

    /** {@code created=false} 면 재시도였고 기존 여행을 돌려준 것이다. */
    record SaveOutcome(Trip trip, PreferenceSnapshot snapshot, boolean created) {}

    /** 같은 키를 다른 내용으로 재사용했을 때 — API-09 가 409 로 거부하라고 한다. */
    class IdempotencyKeyConflictException extends RuntimeException {
        public IdempotencyKeyConflictException(String key) {
            super("같은 Idempotency-Key(" + key + ")가 다른 본문으로 이미 쓰였습니다");
        }
    }
}
