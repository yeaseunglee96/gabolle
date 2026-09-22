package com.gabolle.backend.trip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.trip.domain.Trip;

/**
 * 삭제는 {@code deleted_at} 하나로만 말한다. {@code status} 에 {@code DELETED} 를 두면
 * DB CHECK(trip.status) 위반이라 저장 자체가 안 된다.
 */
class TripTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-03T00:00:00Z");

    private Trip trip() {
        return new Trip("trp_1", "usr_1",
                LocalDate.of(2026, 9, 6), LocalDate.of(2026, 9, 8),
                null, null, null, 2, null, "Asia/Seoul", CREATED_AT);
    }

    @Test
    @DisplayName("삭제는 deletedAt 하나로만 말한다 — status 는 안 바뀐다")
    void deletionDoesNotChangeStatus() {
        Trip t = trip();
        Instant deletedAt = Instant.parse("2026-09-03T01:00:00Z");

        t.markDeleted(deletedAt);

        assertTrue(t.isDeleted());
        assertEquals(deletedAt, t.deletedAt());
        assertEquals(Trip.Status.PLANNING, t.status(), "지워지기 전 단계가 그대로 남아야 분석에서 구분된다");
    }

    @Test
    @DisplayName("두 번 지워도 deletedAt 은 처음 값 그대로다")
    void markingDeletedTwiceKeepsFirstTimestamp() {
        Trip t = trip();
        Instant first = Instant.parse("2026-09-03T01:00:00Z");
        Instant second = Instant.parse("2026-09-03T02:00:00Z");

        t.markDeleted(first);
        t.markDeleted(second);

        assertEquals(first, t.deletedAt());
    }

    @Test
    @DisplayName("Status 열거값에 DELETED 가 없다 — DB CHECK 가 그 값을 안 받는다")
    void statusEnumHasNoDeletedValue() {
        for (Trip.Status status : Trip.Status.values()) {
            assertFalse(status.name().equals("DELETED"));
        }
    }

    @Test
    @DisplayName("삭제된 여행은 READY 로 못 옮긴다")
    void deletedTripCannotBecomeReady() {
        Trip t = trip();
        t.markDeleted(Instant.parse("2026-09-03T01:00:00Z"));

        assertThrows(IllegalStateException.class,
                () -> t.markReady(Instant.parse("2026-09-03T02:00:00Z")));
    }

    @Test
    @DisplayName("READY 로 옮기면 updatedAt 이 갱신된다")
    void movingToReadyUpdatesTimestamp() {
        Trip t = trip();
        Instant readyAt = Instant.parse("2026-09-03T03:00:00Z");

        t.markReady(readyAt);

        assertEquals(Trip.Status.READY, t.status());
        assertEquals(readyAt, t.updatedAt());
    }

    @Test
    @DisplayName("생성 직후 updatedAt 은 createdAt 과 같다")
    void updatedAtStartsEqualToCreatedAt() {
        Trip t = trip();

        assertEquals(CREATED_AT, t.updatedAt());
    }
}
