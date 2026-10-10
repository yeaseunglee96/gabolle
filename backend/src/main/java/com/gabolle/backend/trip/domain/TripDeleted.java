package com.gabolle.backend.trip.domain;

/**
 * 여행이 지워졌다(soft delete). 그 여행을 가리키는 다른 기록이 따라 정리할 수 있게 알린다 —
 * 예: 루트 포스트의 「따라 간 사람」(S15P21E201-2043). 지운 트랜잭션 안에서 동기로 전달된다.
 */
public record TripDeleted(String tripId) {
}
