package com.gabolle.backend.trip.application;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.trip.domain.TripRepository;

/**
 * 가입 시 익명 여행 승계. auth 모듈이 trip 의 JPA 를 직접 알지 않게 하는 자리다.
 *
 * <p>트랜잭션 경계는 여기가 아니라 부르는 쪽({@code LocalAuthService.register})이 정한다 —
 * 기본 전파 REQUIRED 로 합류하므로 승계가 실패하면 계정 생성까지 함께 롤백된다.
 */
@Service
public class AnonymousTripClaimService {

    private final TripRepository repository;

    public AnonymousTripClaimService(TripRepository repository) {
        this.repository = repository;
    }

    /**
     * @param sessionId {@code null}/빈 값이면 0 을 돌려준다 — 세션 토큰 없는 회원가입은 정상 흐름이다
     * @return 승계된 여행 수. 0 이어도 정상이다
     */
    @Transactional
    public int claimForNewUser(String sessionId, String newOwnerId, Instant at) {
        if (sessionId == null || sessionId.isBlank()) {
            return 0;
        }
        return repository.claimAnonymousTrips(sessionId, newOwnerId, at);
    }
}
