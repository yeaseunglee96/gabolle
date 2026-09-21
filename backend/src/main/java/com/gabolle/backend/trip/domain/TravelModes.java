package com.gabolle.backend.trip.domain;

import java.util.Locale;

/**
 * 화면의 이동수단 선호 값을 {@code trip.travel_modes}(마이그레이션 {@code ck_trip_travel_modes}
 * 의 아홉 개가 정본)로 바꾼다. 받는 값이 {@code WALK · CAR · TRANSIT} 셋뿐인 것은 프론트의
 * {@code Transport} 타입이 정확히 이 셋이기 때문이다 — 종류가 늘면 이 클래스만 고친다.
 */
public final class TravelModes {

    private TravelModes() {
    }

    /**
     * @param transportCode 앞뒤 공백과 대소문자는 여기서 정규화한다 — 값의 JSON 따옴표를 벗기는
     *                       것은 부르는 쪽의 몫이다
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
