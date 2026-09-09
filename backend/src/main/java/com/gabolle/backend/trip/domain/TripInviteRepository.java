package com.gabolle.backend.trip.domain;

import java.util.Optional;

/**
 * 초대 표 저장소 — S15P21E201-294 · -299. 인터페이스만 도메인에 둔다. 구현은 {@code infra} 에 있다.
 *
 * <p>초대는 고치지 않는다. 만들고(save), 표로 찾고(findByToken), 그것이 전부다. 만료는 행을 지우는
 * 것이 아니라 {@link TripInvite#isExpiredAt} 이 판정한다.
 */
public interface TripInviteRepository {

    TripInvite save(TripInvite invite);

    Optional<TripInvite> findByToken(String token);
}
