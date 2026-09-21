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
 *
 * <p>🔴 <b>2026-09-21 (S15P21E201-1453) — "범위 모양"의 판정을 넓혔다.</b>
 * {@code "0800-1800"} 은 콜론이 없어 어느 범위 패턴에도 안 걸렸고, 그래서 "프리셋이구나" 로
 * 분류되어 <b>조용히 버려졌다.</b> 화면도 사용자도 몰랐고, 하루 활동 시각만 사라진 채 일정이 만들어졌다
 * ({@code ItineraryDraftService.layoutDay} 가 시각이 없으면 그날 자리를 전부 unknown 으로 만든다).
 *
 * <p>프리셋 이름은 {@code MORNING_TO_EVENING} 처럼 <b>글자</b>다. 숫자와 하이픈뿐이면 프리셋일 수
 * 없으므로 시각을 적으려던 것으로 보고 거절한다. 글자로 된 새 프리셋은 예전처럼 그냥 통과하므로
 * 목록은 여전히 열려 있다.
 */
public final class TimeWindows {

    /** "HH:mm-HH:mm" — 시는 00~23, 분은 00~59, 항상 두 자리여야 이 모양으로 인정한다. */
    private static final Pattern STRICT_RANGE =
            Pattern.compile("^([01]\\d|2[0-3]):([0-5]\\d)-([01]\\d|2[0-3]):([0-5]\\d)$");

    /** 여기 걸리는데 {@link #STRICT_RANGE} 에는 안 걸리면 "범위를 시도했는데 틀렸다"로 본다. */
    private static final Pattern LOOSE_RANGE = Pattern.compile("^\\d{1,2}:\\d{2}-\\d{1,2}:\\d{2}$");

    /**
     * 숫자와 하이픈만으로 된 범위 시도 — {@code "0800-1800"}.
     *
     * <p>🔴 <b>이것이 조용히 버려지던 자리다.</b> 콜론이 없어 {@link #LOOSE_RANGE} 에도 안 걸려서
     * "범위를 시도한 게 아니다"로 분류됐고, 빈 값이 되어 활동 시각이 통째로 사라졌다. 오류가 안 나니
     * 화면도 사용자도 몰랐다.
     *
     * <p>프리셋 이름은 {@code MORNING_TO_EVENING} 처럼 글자다. <b>숫자와 하이픈뿐이면 프리셋일 수
     * 없다</b> — 시각을 적으려던 것이다. 그래서 여기만 따로 잡아 거절한다. 프리셋 목록은 여전히
     * 열려 있고, 글자로 된 새 프리셋은 예전처럼 그냥 통과한다.
     *
     * <p>🔴 <b>400 이 조용한 무시보다 낫다.</b> 거절당하면 사용자가 고칠 기회를 얻는다.
     * 조용히 버리면 성공한 척하고 하루 시간대만 사라진다.
     */
    private static final Pattern DIGITS_ONLY_RANGE = Pattern.compile("^[0-9]{3,4} *- *[0-9]{3,4}$");

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

        if (DIGITS_ONLY_RANGE.matcher(trimmed).matches()) {
            // 콜론만 빠진 시각 — 프리셋일 수 없다. 조용히 버리지 않고 고칠 기회를 준다.
            throw new InvalidTimeWindowException(raw);
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
