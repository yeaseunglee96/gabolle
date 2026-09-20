package com.gabolle.backend.trip.domain;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 앱이 보내는 취향 차원 이름({@code touristPreference})을 {@code preference_answer.dimension} 의
 * CHECK 어휘({@code TOURIST_PREFERENCE})로 바꾼다. 이미 어휘로 온 값은 그대로 통과하므로 같은
 * 값을 두 번 통과시켜도 결과가 같다.
 *
 * <p>모르는 이름은 조용히 버리지 않고 예외로 던진다 — 버리면 사용자가 답한 취향이 사라졌는데
 * 아무도 모른다. {@code TRANSPORT} 는 CHECK 에 없는 값이지만 여기서 거절하지 않는다. 그 답은
 * {@code TripCreationService} 가 {@code trip.travel_modes} 로 옮기고 스냅샷 저장 목록에서 뺀다.
 */
public final class PreferenceDimensions {

    /** 왼쪽은 {@code tripApi.ts} 의 {@code preference('…')} 호출과 글자 그대로 같다. */
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

    /** 정규화 결과로 허용되는 값. CHECK 여덟 값 + {@code TRANSPORT}. */
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

    public static boolean isTransport(String normalized) {
        return TRANSPORT.equals(normalized);
    }

    /**
     * {@link IllegalArgumentException} 하위 타입이라 {@code TripExceptionHandler} 의 기존 400
     * 번역에 그대로 걸린다.
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
