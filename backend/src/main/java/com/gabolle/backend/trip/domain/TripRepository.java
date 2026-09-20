package com.gabolle.backend.trip.domain;

import java.util.List;
import java.util.Optional;

/** 여행 저장소. 구현은 {@code infra} 에 있다 — 여기는 JPA·Spring 을 import 하지 않는다. */
public interface TripRepository {

    /** 여행·제약·소유자·취향을 한 번에 저장한다. 나누면 소유자 없는 여행이 생긴다. */
    Trip save(Trip trip, List<TripConstraint> constraints,
              TripMember owner, PreferenceSnapshot snapshot);

    Optional<Trip> findById(String tripId);

    List<TripConstraint> findConstraints(String tripId);

    List<TripMember> findMembers(String tripId);

    /**
     * 한 사람이 회원으로 들어 있는 여행을 최근 수정순으로 {@code limit} 개까지. 지운 여행은 뺀다.
     *
     * <p>소유자가 아니라 {@link TripMember} 표를 기준으로 읽는다 — 소유자 기준이면 초대받은
     * 사람에게 그 여행이 안 보인다.
     */
    List<MemberTrip> findTripsForMember(String userId, int limit);

    /** 목록 한 줄. 역할을 같이 주는 것은 화면이 여행마다 역할을 다시 묻지 않게 하기 위해서다. */
    record MemberTrip(Trip trip, TripMember.Role role) {
    }

    /**
     * 지운 시각만 저장한다. 행은 지우지 않는다 — 일정·기록·공유 링크가 이 여행을 가리킨다.
     *
     * <p>부르는 쪽이 {@link Trip#markDeleted(java.time.Instant)} 를 먼저 태운다. 여기서 시각을
     * 다시 정하지 않는다.
     */
    void softDelete(Trip trip);

    /**
     * 상태 칸만 저장한다. 부르는 쪽이 {@link Trip#markReady(java.time.Instant)} 를 먼저 태운다.
     *
     * <p>{@link #save} 는 제약·소유자·취향까지 받는 생성용이라, 그걸로 상태 한 칸을 옮기면
     * 딸린 것들을 의도치 않게 덮어쓴다.
     */
    void updateStatus(Trip trip);

    /** 이름 칸만 저장한다. 부르는 쪽이 {@link Trip#rename(String, java.time.Instant)} 를 먼저 태운다. */
    void updateTitle(Trip trip);

    /** 특정 판. 없으면 비어 있다. */
    Optional<PreferenceSnapshot> findSnapshot(String tripId, int version);

    /**
     * 계정 기본 취향({@code scope=USER})의 최신 판.
     *
     * <p>여행 스냅샷과 키가 다르다 — 이쪽은 {@code trip_id} 가 NULL 이고 {@code user_id} 로 찾는다.
     */
    Optional<PreferenceSnapshot> findUserDefaults(String userId);

    /**
     * 계정 기본 취향을 새 판으로 저장한다. 기존 판은 고치지 않는다 — 이미 만들어진 여행의
     * 스냅샷이 "그때의 기본값" 이라고 주장하려면 그때의 판이 남아 있어야 한다.
     *
     * @param answers 그 판의 전부다. 부분 갱신이 아니다
     * @return 판 번호와 ID 가 채워진 스냅샷. 판 번호는 구현이 정한다(마지막 판 + 1)
     */
    PreferenceSnapshot saveUserDefaults(String userId,
                                        List<PreferenceSnapshot.PreferenceAnswer> answers,
                                        java.time.Instant at);

    /** 가장 최신 판. */
    Optional<PreferenceSnapshot> findLatestSnapshot(String tripId);

    /**
     * 스냅샷 ID 로 그 판을 직접 읽는다 — 추천 엔진 전용.
     *
     * <p>{@link #findLatestSnapshot(String)} 로 대신하면 안 된다. 추천 Job 은 스냅샷 ID 를
     * 기록해 두고 나중에 실행되므로, 그 사이 취향이 바뀌면 "최신 판" 은 기록해 둔 판이 아니다.
     */
    Optional<PreferenceSnapshot> findSnapshotById(String preferenceSnapshotId);

    /**
     * 제약 스냅샷 ID 로 그 판의 제약 전부를 읽는다 — 추천 엔진 전용.
     *
     * <p>{@link #findConstraints(String)} 로 대신하면 안 된다. 그쪽은 첫 번째 스냅샷을 집으므로
     * Job 이 기록해 둔 것과 다를 수 있다.
     */
    List<TripConstraint> findConstraintsBySnapshotId(String constraintSnapshotId);

    /**
     * 가장 최신 제약 스냅샷의 식별자.
     *
     * <p>제약을 하나도 안 답한 여행은 값이 없다({@code save} 가 행 자체를 안 만든다).
     * 그런 여행은 추천을 요청할 수 없다.
     */
    Optional<String> findLatestConstraintSnapshotId(String tripId);

    /**
     * 멱등 키 확보와 저장이 한 동작이어야 한다. 키를 먼저 조회하고 나중에 묶으면, 같은 키로
     * 동시에 온 요청이 전부 "없음" 을 받고 전부 여행을 만든다.
     *
     * <p>DB 구현에서는 한 트랜잭션 안의 {@code INSERT INTO trip_idempotency} 가
     * {@code UNIQUE (user_id, idempotency_key)} 에 걸리는 것이 이 자리를 대신한다.
     *
     * @param idempotencyKey {@code null} 이면 중복 방지 없이 그냥 저장한다
     * @return 새로 저장했으면 {@code created=true}, 이미 있었으면 그 여행과 {@code false}
     * @throws IdempotencyKeyConflictException 같은 키가 다른 본문으로 이미 쓰였을 때
     */
    SaveOutcome saveWithIdempotency(String userId, String idempotencyKey, String requestFingerprint,
                                    Trip trip, List<TripConstraint> constraints,
                                    TripMember owner, PreferenceSnapshot snapshot);

    /** {@code created=false} 면 재시도였고 기존 여행을 돌려준 것이다. */
    record SaveOutcome(Trip trip, PreferenceSnapshot snapshot, boolean created) {}

    /**
     * 가입 시 익명 여행 승계. {@code sessionId} 가 만든 여행의 소유자를 {@code newOwnerId} 로 옮긴다.
     *
     * <p>여행({@code createdBy}·{@code ownerType})과 OWNER {@link TripMember} 행을 함께 옮겨야
     * 한다 — 참여자 행이 그대로면 승계돼도 목록에 안 보인다.
     *
     * @return 옮긴 여행 수. 익명 여행이 없어 0 이어도 정상이다
     */
    int claimAnonymousTrips(String sessionId, String newOwnerId, java.time.Instant at);

    /** 같은 키를 다른 내용으로 재사용했을 때 — 409 로 거부한다. */
    class IdempotencyKeyConflictException extends RuntimeException {
        public IdempotencyKeyConflictException(String key) {
            super("같은 Idempotency-Key(" + key + ")가 다른 본문으로 이미 쓰였습니다");
        }
    }
}
