package com.gabolle.backend.trip.domain;

/**
 * 취향·제약이 계정 기본값인가, 이번 여행 전용인가 — S15P21E201-542 2.2.
 *
 * <p>🔴 이 구분이 없으면 여행에서 고친 값이 계정 기본값을 조용히 덮어쓴다.
 * "이번 여행만 매운 음식 빼기" 가 다음 여행에도 남아 있으면 사용자는 그 이유를 모른다.
 *
 * <p>{@link PreferenceSnapshot}·{@link TripConstraint} 가 공유한다 — 두 곳에서
 * 같은 개념을 각자 다른 이름으로 두면 나중에 하나만 고쳐지고 어긋난다.
 *
 * <p>🔴 TRIP-01(여행 생성)이 만드는 스냅샷·제약은 <b>항상 {@link #TRIP}</b> 이다.
 * {@code trip_id} 가 항상 있는 자리라서 그 외의 값은 DB 제약(성격상
 * {@code (scope = 'TRIP') = (trip_id IS NOT NULL)})을 어긴다. {@link #USER}(계정
 * 기본값)는 별도의 "내 취향 설정" 흐름이 생길 때 그쪽에서만 만든다 — 지금 이 패키지
 * 에는 그 흐름이 없다.
 */
public enum PersonalizationScope {
    /** 계정 기본값. trip_id 가 없다. */
    USER,
    /** 이번 여행 전용. trip_id 가 있다. */
    TRIP
}
