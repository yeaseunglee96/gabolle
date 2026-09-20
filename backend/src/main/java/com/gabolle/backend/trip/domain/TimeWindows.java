package com.gabolle.backend.trip.domain;

import java.time.LocalTime;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 하루 활동 시간대 문자열을 시:분 범위로 해석한다. 화면은 {@code "09:00-18:00"} 같은 범위와
 * {@code "MORNING_TO_EVENING"} 같은 프리셋 이름을 섞어 보낸다 — 프리셋과 실제 시각의 매핑은
 * 아직 확정되지 않았다.
 *
 * <p>범위 모양이 아니면 거절하지 않고 빈 값으로 넘긴다. 프리셋 목록이 열려 있는 한 여기서 400 으로
 * 막으면 서버가 그 미확정을 임의로 닫는 것이 된다. 반대로 범위 모양인데 값이 틀렸으면 예외를 던진다 —
 * 조용히 빈 값으로 넘기면 활동 시각이 비는 것은 같은데 원인(화면 버그)까지 감춰진다.
 */
public final class TimeWindows {

    /** "HH:mm-HH:mm" — 시는 00~23, 분은 00~59, 항상 두 자리여야 이 모양으로 인정한다. */
    private static final Pattern STRICT_RANGE =
            Pattern.compile("^([01]\\d|2[0-3]):([0-5]\\d)-([01]\\d|2[0-3]):([0-5]\\d)$");

    /** 여기 걸리는데 {@link #STRICT_RANGE} 에는 안 걸리면 "범위를 시도했는데 틀렸다"로 본다. */
    private static final Pattern LOOSE_RANGE = Pattern.compile("^\\d{1,2}:\\d{2}-\\d{1,2}:\\d{2}$");

    private TimeWindows() {
    }

    /**
     * @return 범위 모양이고 값도 맞으면 그 시각 두 개, 범위 모양이 아니면(프리셋 등) 빈 값
     * @throws InvalidTimeWindowException 범위 모양인데 값이 틀렸다
     */
    public static Optional<TimeWindow> parseRange(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }

        Matcher strict = STRICT_RANGE.matcher(trimmed);
        if (strict.matches()) {
            LocalTime start = LocalTime.of(
                    Integer.parseInt(strict.group(1)), Integer.parseInt(strict.group(2)));
            LocalTime end = LocalTime.of(
                    Integer.parseInt(strict.group(3)), Integer.parseInt(strict.group(4)));
            if (!end.isAfter(start)) {
                throw new InvalidTimeWindowException(raw);
            }
            return Optional.of(new TimeWindow(start, end));
        }

        if (LOOSE_RANGE.matcher(trimmed).matches()) {
            // 골격은 범위인데 자리수(예: "9:00")나 범위(예: "25:00")가 틀렸다.
            throw new InvalidTimeWindowException(raw);
        }

        // 골격 자체가 없다 — 범위를 시도한 게 아니라 프리셋 이름 등이다.
        return Optional.empty();
    }

    public record TimeWindow(LocalTime start, LocalTime end) {
    }

    public static class InvalidTimeWindowException extends IllegalArgumentException {

        private final String raw;

        public InvalidTimeWindowException(String raw) {
            super("timeWindow 가 HH:mm-HH:mm 범위 모양인데 값이 올바르지 않다: " + raw);
            this.raw = raw;
        }

        public String raw() {
            return raw;
        }
    }
}
