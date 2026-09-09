package com.gabolle.backend.trip.domain;

import java.util.Locale;

/**
 * 화면의 이동수단 선호 값을 {@code trip.travel_modes}(마이그레이션
 * {@code ck_trip_travel_modes} 의 아홉 개가 정본)로 바꾼다 — S15P21E201-664.
 *
 * <p>🔴 매핑표를 {@code WALK · CAR · TRANSIT} 셋으로 좁힌 이유 — 프론트의
 * {@code Transport} 타입이 정확히 이 셋이다(다른 값을 화면이 만들어 낼 수 없다).
 * 나중에 화면이 종류를 늘리면(예: 자전거) 부르는 쪽({@code TripCreationService})은
 * 그대로 두고 <b>이 표만</b> 고치면 된다 — 매핑 규칙이 이 클래스 하나에 몰려 있어서다.
 *
 * <table>
 *   <tr><th>화면 값</th><th>{@code travel_modes}</th></tr>
 *   <tr><td>{@code WALK}</td><td>{@code [WALK]}</td></tr>
 *   <tr><td>{@code CAR}</td><td>{@code [PRIVATE_CAR]}</td></tr>
 *   <tr><td>{@code TRANSIT}</td><td>{@code [BUS, SUBWAY]}</td></tr>
 * </table>
 */
public final class TravelModes {

    private TravelModes() {
    }

    /**
     * @param transportCode 화면 값. 앞뒤 공백과 대소문자는 여기서 정규화한다
     *                       ({@code trim().toUpperCase()}) — 값의 JSON 따옴표를
     *                       벗기는 것은 부르는 쪽({@code TripCreationService})의 몫이다.
     * @return {@code trip.travel_modes} 에 그대로 저장할 수 있는 값 배열
     * @throws UnsupportedTravelModeException {@code WALK · CAR · TRANSIT} 셋 밖의 값
     */
    public static String[] fromTransportPreference(String transportCode) {
        String normalized = transportCode == null ? null : transportCode.trim().toUpperCase(Locale.ROOT);
        if (normalized == null) {
            throw new UnsupportedTravelModeException(transportCode);
        }
        return switch (normalized) {
            case "WALK" -> new String[] {"WALK"};
            case "CAR" -> new String[] {"PRIVATE_CAR"};
            case "TRANSIT" -> new String[] {"BUS", "SUBWAY"};
            default -> throw new UnsupportedTravelModeException(transportCode);
        };
    }

    /** {@code WALK · CAR · TRANSIT} 셋 밖의 이동수단 값 — {@link #code()}로 원래 값을 남긴다. */
    public static class UnsupportedTravelModeException extends IllegalArgumentException {

        private final String code;

        public UnsupportedTravelModeException(String code) {
            super("지원하지 않는 이동수단이다(WALK/CAR/TRANSIT 만 받는다): " + code);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }
}
