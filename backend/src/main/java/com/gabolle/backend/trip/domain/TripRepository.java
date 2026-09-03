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

    /** 가장 최신 판. */
    Optional<PreferenceSnapshot> findLatestSnapshot(String tripId);

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
