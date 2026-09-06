package com.gabolle.backend.trip.domain;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 앱이 보내는 취향 차원 이름을 DB 가 받는 어휘로 바꾼다 — S15P21E201-665.
 *
 * <p>🔴 <b>이 클래스가 없어서 실제 앱은 여행을 만들 수 없었다.</b> 앱
 * ({@code frontend/src/api/tripApi.ts})은 취향을 {@code category · atmosphere · locality ·
 * quietness · touristPreference · foodPreference · transport · slopePreference · shadePreference}
 * 처럼 <b>소문자 camelCase</b> 로 보낸다. 그런데 {@code preference_answer.dimension} 의 CHECK
 * ({@code ck_preference_answer_dimension}, V20260903120000)는 {@code CATEGORY · ATMOSPHERE ·
 * … · SHADE_PREFERENCE} <b>대문자 여덟 값만</b> 허용한다. 서버가 값을 그대로 넘겼으니 취향 한 줄이
 * CHECK 에 걸리고, 여행·참여자·제약·취향은 한 트랜잭션이라 <b>여행 자체가 롤백</b>됐다.
 *
 * <p>왜 지금까지 안 잡혔나 — 여행 생성 테스트가 전부 대문자로 만들었고, 앱이 실제로 보내는
 * 모양으로 컨트롤러를 통과시키는 테스트가 하나도 없었다. 그 테스트가 이제
 * {@code TripCreateAppPayloadIntegrationTest} 다.
 *
 * <h2>규칙</h2>
 * <ul>
 *   <li>앱 이름은 표로 바꾼다. 표에 있는 이름만 안다.</li>
 *   <li>이미 CHECK 어휘(대문자)로 온 값은 그대로 통과한다 — 기존 테스트와 다른 클라이언트가
 *       그 모양을 쓴다. 같은 값을 두 번 통과시켜도 같아야 한다(멱등).</li>
 *   <li>모르는 이름은 {@link UnknownPreferenceDimensionException} — <b>조용히 버리지 않는다.</b>
 *       버리면 사용자가 답한 취향이 사라졌는데 아무도 모른다. 이 배선이 지금까지 안 됐던
 *       이유가 정확히 "조용히 실패했기 때문" 이다.</li>
 *   <li>{@code transport} → {@code TRANSPORT} 도 표에 있다. CHECK 에는 없는 값이지만 여기서
 *       거절하지 않는다 — 그 답은 {@code TripCreationService} 가 {@code trip.travel_modes} 로
 *       옮기고 스냅샷 저장 목록에서 뺀다(S15P21E201-664). 이 클래스의 일은 이름을 어휘로
 *       바꾸는 것까지고, 어느 칸에 저장하느냐는 서비스의 일이다.</li>
 * </ul>
 *
 * <p>🔴 이 클래스는 Spring·JPA 를 import 하지 않는다 — {@code TimeWindows}·{@code TravelModes}
 * 와 같은 이유다. DB 없이 검증할 수 있어야 한다.
 */
public final class PreferenceDimensions {

    /** 앱 이름 → CHECK 어휘. 왼쪽은 {@code tripApi.ts} 의 {@code preference('…')} 호출과 글자 그대로 같다. */
    private static final Map<String, String> APP_TO_DB = Map.of(
            "category", "CATEGORY",
            "atmosphere", "ATMOSPHERE",
            "locality", "LOCALITY",
            "quietness", "QUIETNESS",
            "touristPreference", "TOURIST_PREFERENCE",
            "foodPreference", "FOOD_PREFERENCE",
            "transport", "TRANSPORT",
            "slopePreference", "SLOPE_PREFERENCE",
            "shadePreference", "SHADE_PREFERENCE");

    /**
     * 정규화 결과로 허용되는 값. CHECK 여덟 값 + {@code TRANSPORT}.
     * {@code TRANSPORT} 가 여기 있는 이유는 클래스 javadoc 참고.
     */
    private static final Set<String> DB_VOCABULARY = Set.of(
            "CATEGORY", "ATMOSPHERE", "LOCALITY", "QUIETNESS",
            "TOURIST_PREFERENCE", "FOOD_PREFERENCE", "SLOPE_PREFERENCE", "SHADE_PREFERENCE",
            "TRANSPORT");

    public static final String TRANSPORT = "TRANSPORT";

    private PreferenceDimensions() {
    }

    /**
     * @param raw 앱 이름({@code touristPreference}) 또는 이미 어휘인 값({@code TOURIST_PREFERENCE})
     * @return CHECK 어휘(또는 {@code TRANSPORT})
     * @throws UnknownPreferenceDimensionException 표에도 어휘에도 없는 이름
     */
    public static String normalize(String raw) {
        if (raw == null) {
            throw new UnknownPreferenceDimensionException(raw);
        }
        String trimmed = raw.trim();
        String mapped = APP_TO_DB.get(trimmed);
        if (mapped != null) {
            return mapped;
        }
        String upper = trimmed.toUpperCase(Locale.ROOT);
        if (DB_VOCABULARY.contains(upper)) {
            return upper;
        }
        throw new UnknownPreferenceDimensionException(raw);
    }

    /** {@code TRANSPORT} 인가 — 스냅샷에 저장하지 않고 {@code travel_modes} 로 보내는 차원. */
    public static boolean isTransport(String normalized) {
        return TRANSPORT.equals(normalized);
    }

    /**
     * 표에도 어휘에도 없는 차원 이름 — 400 으로 답할 자리다.
     * {@link IllegalArgumentException} 의 하위 타입이라 {@code TripExceptionHandler} 의
     * 기존 400 번역({@code TRIP_VALIDATION_FAILED}, {@code fields} 에 이 메시지)에 그대로 걸린다.
     */
    public static class UnknownPreferenceDimensionException extends IllegalArgumentException {

        private final String raw;

        public UnknownPreferenceDimensionException(String raw) {
            super("preferences.dimension 을 모른다: " + raw
                    + " (받는 이름: " + String.join(", ", APP_TO_DB.keySet().stream().sorted().toList()) + ")");
            this.raw = raw;
        }

        public String raw() {
            return raw;
        }
    }
}
