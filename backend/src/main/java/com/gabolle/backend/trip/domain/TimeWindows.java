package com.gabolle.backend.trip.domain;

import java.time.LocalTime;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 하루 활동 시간대 문자열을 실제 시각으로 해석한다 — S15P21E201-664.
 *
 * <p>화면은 두 가지 모양을 섞어 보낼 수 있다. 하나는 {@code "09:00-18:00"} 같은
 * 실제 시:분 범위이고(TRIP-01 프론트가 {@code dayStartTime}·{@code dayEndTime} 을
 * 합쳐 이 모양으로 보낸다), 다른 하나는 {@code "MORNING_TO_EVENING"} 같은 프리셋
 * 이름이다 — 프리셋과 실제 시각의 매핑표는 아직 BE·FE·DATA 3자가 확정하지 않았다
 * (마이그레이션 {@code V20260904010000} 주석 — "프리셋 목록과 각각의 시간 범위는
 * 여기서 정하지 않는다").
 *
 * <p>🔴 그래서 이 클래스는 <b>세 경우를 구분</b>한다. 이 구분이 이 작업의 핵심 판단이다.
 * <ul>
 *   <li><b>범위 모양이고 값도 맞다</b>(예: {@code "09:00-18:00"}) — 시각 두 개로
 *       해석해 {@link Optional#of}로 돌려준다.</li>
 *   <li><b>범위 모양이 아니다</b>(예: {@code "MORNING_TO_EVENING"}, {@code null},
 *       빈 문자열, 그 밖의 임의 문자열) — {@link Optional#empty()}. <b>거절하지
 *       않는다.</b> 프리셋 목록이 열린 항목인 이상, 모르는 프리셋을 여기서 400 으로
 *       막으면 서버가 그 미확정을 임의로 닫아 버리는 것이 된다.</li>
 *   <li><b>범위 모양인데 값이 틀렸다</b>(예: {@code "18:00-09:00"}(끝이 시작보다
 *       앞이거나 같다), {@code "25:00-30:00"}(시:분 범위를 벗어났다),
 *       {@code "9:00-18:00"}(시:분이 두 자리가 아니다)) — {@link InvalidTimeWindowException}.
 *       이건 화면이 값을 잘못 만든 것이 거의 확실하다. 조용히 {@link Optional#empty()}
 *       로 넘기면, 일정을 짤 때 쓰는 "끝이 시작보다 뒤가 아니면 시각을 안 채운다"는
 *       판정이 이 오류를 그대로 삼켜 버린다 — 그러면 <b>지금과 똑같이 활동 시각이
 *       전부 비는데, 이번에는 원인(화면 버그)까지 감춰지는</b> 최악의 결과가 된다.</li>
 * </ul>
 *
 * <p>판정 순서 — 먼저 엄격한 모양({@code ^([01]\d|2[0-3]):([0-5]\d)-([01]\d|2[0-3]):([0-5]\d)$})에
 * 맞는지 본다. 맞으면 시작·끝을 비교해 범위가 유효한지 확인한다. 안 맞으면, 그래도
 * "범위를 시도한 흔적"({@code \d{1,2}:\d{2}-\d{1,2}:\d{2}} 정도의 느슨한 모양)이 있는지
 * 본다 — 있으면 "범위 모양인데 틀렸다"(예외), 없으면 "애초에 범위가 아니다"(빈 값)로
 * 가른다.
 */
public final class TimeWindows {

    /** "HH:mm-HH:mm" — 시는 00~23, 분은 00~59, 항상 두 자리여야 이 모양으로 인정한다. */
    private static final Pattern STRICT_RANGE =
            Pattern.compile("^([01]\\d|2[0-3]):([0-5]\\d)-([01]\\d|2[0-3]):([0-5]\\d)$");

    /**
     * 자리수·범위는 안 봐도 "시:분-시:분" 골격만 갖췄으면 잡히는 느슨한 모양.
     * 여기 걸리는데 {@link #STRICT_RANGE} 에는 안 걸리면 "범위를 시도했는데 틀렸다"로 본다.
     */
    private static final Pattern LOOSE_RANGE = Pattern.compile("^\\d{1,2}:\\d{2}-\\d{1,2}:\\d{2}$");

    private TimeWindows() {
    }

    /**
     * {@code raw} 를 시:분 범위로 해석한다.
     *
     * @return 범위 모양이고 값도 맞으면 그 시각 두 개, 범위 모양이 아니면(프리셋 등)
     *         {@link Optional#empty()}
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
                // 끝이 시작보다 앞이거나(예: 18:00-09:00) 같다(예: 09:00-09:00).
                throw new InvalidTimeWindowException(raw);
            }
            return Optional.of(new TimeWindow(start, end));
        }

        if (LOOSE_RANGE.matcher(trimmed).matches()) {
            // 골격은 범위인데 자리수(예: "9:00")나 범위(예: "25:00")가 틀렸다.
            throw new InvalidTimeWindowException(raw);
        }

        // 콜론·대시 골격 자체가 없다 — 범위를 시도한 게 아니라 프리셋 이름 등이다.
        return Optional.empty();
    }

    public record TimeWindow(LocalTime start, LocalTime end) {
    }

    /**
     * 범위 모양(시:분-시:분)인데 값이 올바르지 않을 때 — 화면이 값을 잘못 만든 것이
     * 거의 확실한 경우다. {@link #raw()}로 무엇이 왔는지 그대로 남긴다.
     */
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
