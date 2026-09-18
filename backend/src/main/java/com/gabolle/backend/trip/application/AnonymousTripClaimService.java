package com.gabolle.backend.trip.application;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.trip.domain.TripRepository;

/**
 * 가입 시 익명 여행 승계 — S15P21E201-317.
 *
 * <p>🔴 <b>auth 모듈이 trip 의 인프라(JPA)를 직접 알지 않게 하는 자리</b>다. {@code LocalAuthService}
 * (auth.service)가 이 서비스만 부르고, 실제 승계는 {@link TripRepository}(trip.domain — JPA를
 * import 하지 않는 인터페이스) 뒤로 숨는다.
 *
 * <p>{@code @Transactional} 이지만 이 클래스가 트랜잭션 경계를 정하지 않는다 — 부르는 쪽인
 * {@code LocalAuthService.register} 가 이미 트랜잭션 안에 있으므로 그 경계에 그대로 합류한다
 * (Spring 기본 전파 REQUIRED). 계정 생성과 승계가 <b>한 트랜잭션</b>이어야 한다는 요구(티켓
 * 완료 기준 2번)가 이 합류로 지켜진다 — 승계 도중 실패하면 계정 생성까지 함께 롤백된다.
 */
@Service
public class AnonymousTripClaimService {

    private final TripRepository repository;

    public AnonymousTripClaimService(TripRepository repository) {
        this.repository = repository;
    }

    /**
     * @param sessionId  익명 세션 ID. {@code null}/빈 값이면 승계할 것이 없다는 뜻이라 그냥 0을 돌려준다
     *                   — 세션 토큰이 없는 회원가입은 완전히 정상적인 흐름이다(완료 기준 3번)
     * @param newOwnerId 방금 만든 회원의 user_id
     * @return 승계된 여행 수
     */
    @Transactional
    public int claimForNewUser(String sessionId, String newOwnerId, Instant at) {
        if (sessionId == null || sessionId.isBlank()) {
            return 0;
        }
        return repository.claimAnonymousTrips(sessionId, newOwnerId, at);
    }
}
