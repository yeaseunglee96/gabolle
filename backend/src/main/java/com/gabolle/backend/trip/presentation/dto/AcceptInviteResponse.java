package com.gabolle.backend.trip.presentation.dto;

/**
 * 초대 수락 응답. {@code alreadyMember} 가 {@code true} 면 새로 들어온 것이 아니라 이미 있던
 * 참여를 그대로 돌려준 것이다 — 역할은 바뀌지 않는다.
 */
public record AcceptInviteResponse(String tripId, String role, boolean alreadyMember, String joinedAt) {
}
