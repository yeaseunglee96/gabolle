package com.gabolle.backend.trip.domain;

import java.time.Instant;

/**
 * 복제 씨앗 — S15P21E201-338 (F-COL-04). 공유 일정에서 가져온 장소 하나와 원본에서의 순서.
 *
 * <p>여기에 시간표·예산·인원은 없다. 그것은 요청자의 조건으로 새로 계산한다 — "4인 예산으로 짜인
 * 일정을 혼자 가는 사람이 그대로 쓰면 첫날부터 안 맞는다" 가 티켓의 출발점이다.
 *
 * @param sourceTripId 원본 여행. 원본이 지워지면 {@code null} 이 된다 — 복제된 여행은 그대로 남는다
 * @param sourceShareLinkId 어느 공유 주소로 들어왔나. 없을 수 있다
 */
public record TripSeedPlace(String tripId, String placeId, int sequence, String sourceTripId,
                            String sourceShareLinkId, Instant createdAt) {

    public TripSeedPlace {
        if (sequence < 1) {
            throw new IllegalArgumentException("순서는 1부터다: " + sequence);
        }
    }
}
