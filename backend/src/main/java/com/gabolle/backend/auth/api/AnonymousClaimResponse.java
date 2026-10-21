package com.gabolle.backend.auth.api;

/**
 * {@code POST /api/v1/auth/anonymous/claim} 응답. 0 도 성공이다 — 비회원으로 만든 여행이 없었거나
 * 이미 넘긴 세션이다.
 */
public record AnonymousClaimResponse(int claimedTrips) {
}
