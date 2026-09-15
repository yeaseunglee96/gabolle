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

    /**
     * 한 사람이 회원으로 들어 있는 여행을 최근에 손댄 순으로 읽는다 — S15P21E201-738.
     *
     * <p>🔴 <b>소유자만 보는 것이 아니다.</b> 판정 기준을 {@code trip.owner_user_id} 로 두면
     * 초대받아 들어온 사람에게 그 여행이 목록에 안 나오고, 그러면 초대받은 사람은
     * 여행에 들어갈 경로가 아예 없다. {@link TripMember} 표를 기준으로 읽는다 —
     * {@link #findMembers(String)} 가 이미 그 표를 여행 쪽에서 보는 것과 같은 표다.
     *
     * <p>지운 여행({@code deleted_at} 이 채워진 행)은 빼고, {@code limit} 개까지만 준다.
     * 상한을 두는 이유는 한 사람이 들어 있는 여행 수에 상한이 없기 때문이다 —
     * 목록 한 번에 표 전체를 읽어 오는 자리를 만들지 않는다.
     */
    List<MemberTrip> findTripsForMember(String userId, int limit);

    /**
     * 목록 한 줄 — 여행과, 그 여행에서 요청자가 가진 역할.
     *
     * <p>역할을 함께 주는 이유는 화면이 편집 버튼을 켤지 정해야 하기 때문이다.
     * 목록을 받은 뒤 여행마다 역할을 다시 묻게 만들면 N 번을 더 부른다.
     */
    record MemberTrip(Trip trip, TripMember.Role role) {
    }

    /**
     * 지운 시각을 저장한다 — S15P21E201-746. 행을 지우지 않는다(TRIP-05).
     *
     * <p>🔴 <b>무엇을 지울지는 이 메서드가 정하지 않는다.</b> 부르는 쪽이 도메인 규칙
     * ({@link Trip#markDeleted(java.time.Instant)})을 먼저 태우고, 그 결과가 든 여행을
     * 그대로 넘긴다. 여기서 시각을 다시 정하면 "이미 지워진 여행은 시각을 덮어쓰지
     * 않는다" 는 규칙이 도메인과 저장소 두 곳에 생기고, 언젠가 한쪽만 바뀐다.
     *
     * <p>행을 실제로 지우지 않는 이유는 일정·기록·공유 링크가 이 여행을 가리키고 있기
     * 때문이다. 지우면 그것들이 전부 가리킬 곳을 잃는다.
     */
    void softDelete(Trip trip);

    /**
     * 상태 칸만 저장한다 — S15P21E201-964.
     *
     * <p>{@link #softDelete(Trip)} 와 같은 모양이다. 부르는 쪽이 도메인 규칙
     * ({@link Trip#markReady(java.time.Instant)})을 먼저 태우고, 그 결과가 든 여행을
     * 그대로 넘긴다. 어떤 상태로 갈 수 있는지는 도메인이 정하고 여기서 다시 정하지 않는다.
     *
     * <p>{@link #save} 를 쓰지 않는 이유는 그것이 제약·소유자·취향 판까지 함께 받는
     * 생성용 자리이기 때문이다. 상태 한 칸을 옮기려고 그것들을 다시 만들어 넘기면,
     * 넘긴 쪽이 의도하지 않은 값으로 딸린 것들을 덮어쓸 수 있다.
     */
    void updateStatus(Trip trip);

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

    /**
     * 가입 시 익명 여행 승계 — S15P21E201-317.
     *
     * <p>{@code sessionId} 가 만든({@code ownerType=ANONYMOUS}) 여행을 전부 찾아 소유자를
     * {@code newOwnerId}(방금 만든 회원)로 옮긴다. 여행 자체({@code createdBy}·{@code ownerType})와
     * OWNER 참여자 행({@code TripMember})을 함께 옮겨야 한다 — 참여자 행이 그대로면
     * "조회 권한 판정이 이 표를 본다"({@link TripMember} 문서)는 전제 때문에 승계돼도 목록에
     * 안 보인다.
     *
     * <p>🔴 익명 여행이 하나도 없어도 <b>정상</b>이다 — 0 을 돌려준다. 이 메서드를 부르는
     * 쪽(회원가입)은 그 결과로 실패 여부를 판단하지 않는다.
     *
     * @return 옮긴 여행 수
     */
    int claimAnonymousTrips(String sessionId, String newOwnerId, java.time.Instant at);

    /** 같은 키를 다른 내용으로 재사용했을 때 — API-09 가 409 로 거부하라고 한다. */
    class IdempotencyKeyConflictException extends RuntimeException {
        public IdempotencyKeyConflictException(String key) {
            super("같은 Idempotency-Key(" + key + ")가 다른 본문으로 이미 쓰였습니다");
        }
    }
}
