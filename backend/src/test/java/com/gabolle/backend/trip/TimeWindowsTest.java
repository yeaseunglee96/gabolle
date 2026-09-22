package com.gabolle.backend.trip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.trip.domain.TimeWindows;

/**
 * {@code parseRange} 는 셋을 가른다 — 값이 맞는 범위(해석) / 범위 모양이 아님(프리셋일 수
 * 있으므로 거절하지 않고 빈 값) / 범위 모양인데 값이 틀림(예외).
 */
class TimeWindowsTest {

    @Test
    @DisplayName("범위 모양이고 값도 맞으면 시각 두 개로 해석된다")
    void validRangeParses() {
        var window = TimeWindows.parseRange("09:00-18:00").orElseThrow();
        assertEquals(LocalTime.of(9, 0), window.start());
        assertEquals(LocalTime.of(18, 0), window.end());
    }

    @Test
    @DisplayName("하루 끝단 경계 00:00-23:59 는 유효하다")
    void fullDayBoundaryIsValid() {
        var window = TimeWindows.parseRange("00:00-23:59").orElseThrow();
        assertEquals(LocalTime.of(0, 0), window.start());
        assertEquals(LocalTime.of(23, 59), window.end());
    }

    @Test
    @DisplayName("앞뒤 공백은 trim 뒤 정상 해석된다")
    void surroundingWhitespaceIsTrimmed() {
        var window = TimeWindows.parseRange(" 09:00-18:00 ").orElseThrow();
        assertEquals(LocalTime.of(9, 0), window.start());
        assertEquals(LocalTime.of(18, 0), window.end());
    }

    @Test
    @DisplayName("끝과 시작이 같으면 거부된다")
    void equalStartAndEndIsRejected() {
        var e = assertThrows(TimeWindows.InvalidTimeWindowException.class,
                () -> TimeWindows.parseRange("09:00-09:00"));
        assertEquals("09:00-09:00", e.raw());
    }

    @Test
    @DisplayName("끝이 시작보다 앞이면 거부된다")
    void endBeforeStartIsRejected() {
        assertThrows(TimeWindows.InvalidTimeWindowException.class,
                () -> TimeWindows.parseRange("09:00-08:59"));
    }

    @Test
    @DisplayName("끝이 시작보다 앞인 하루 전체 반전(18:00-09:00)도 거부된다")
    void reversedFullRangeIsRejected() {
        assertThrows(TimeWindows.InvalidTimeWindowException.class,
                () -> TimeWindows.parseRange("18:00-09:00"));
    }

    @Test
    @DisplayName("시:분이 두 자리가 아니면(9:00) 범위 모양을 시도한 것으로 보고 거부된다")
    void singleDigitHourIsRejected() {
        assertThrows(TimeWindows.InvalidTimeWindowException.class,
                () -> TimeWindows.parseRange("9:00-18:00"));
    }

    @Test
    @DisplayName("시:분 범위를 벗어나면(25:00-30:00) 거부된다")
    void outOfRangeHourIsRejected() {
        assertThrows(TimeWindows.InvalidTimeWindowException.class,
                () -> TimeWindows.parseRange("25:00-30:00"));
    }

    @Test
    @DisplayName("프리셋 이름은 거절하지 않고 빈 값으로 본다")
    void presetNameIsEmptyNotRejected() {
        assertTrue(TimeWindows.parseRange("MORNING_TO_EVENING").isEmpty());
    }

    @Test
    @DisplayName("null 은 빈 값이다")
    void nullIsEmpty() {
        assertTrue(TimeWindows.parseRange(null).isEmpty());
    }

    @Test
    @DisplayName("빈 문자열은 빈 값이다")
    void blankIsEmpty() {
        assertTrue(TimeWindows.parseRange("").isEmpty());
    }
    @Test
    @DisplayName("🔴 콜론만 빠진 시각(0800-1800)은 조용히 버려지지 않고 거부된다")
    void digitsOnlyRangeIsRejectedNotSwallowed() {
        assertThrows(TimeWindows.InvalidTimeWindowException.class,
                () -> TimeWindows.parseRange("0800-1800"));
    }

    @Test
    @DisplayName("🔴 세 자리여도(900-1800) 시각을 적으려던 것으로 본다")
    void threeDigitRangeIsRejected() {
        assertThrows(TimeWindows.InvalidTimeWindowException.class,
                () -> TimeWindows.parseRange("900-1800"));
    }

    @Test
    @DisplayName("하이픈 둘레에 공백이 있어도 같게 본다")
    void spacesAroundHyphenStillRejected() {
        assertThrows(TimeWindows.InvalidTimeWindowException.class,
                () -> TimeWindows.parseRange("0800 - 1800"));
    }

    @Test
    @DisplayName("🔴 글자로 된 프리셋은 여전히 그냥 통과한다 — 프리셋 목록은 열려 있다")
    void letterPresetsStillPass() {
        assertTrue(TimeWindows.parseRange("EARLY_BIRD").isEmpty());
        assertTrue(TimeWindows.parseRange("NIGHT_OWL_2026").isEmpty());
    }

    @Test
    @DisplayName("끝이 시작보다 이르면 거부된다 — 프론트만 믿지 않는다")
    void reversedRangeIsRejected() {
        assertThrows(TimeWindows.InvalidTimeWindowException.class,
                () -> TimeWindows.parseRange("18:00-09:00"));
    }

    @Test
    @DisplayName("시작과 끝이 같아도 거부된다 — 하루가 0분일 수는 없다")
    void equalRangeIsRejected() {
        assertThrows(TimeWindows.InvalidTimeWindowException.class,
                () -> TimeWindows.parseRange("09:00-09:00"));
    }
}
