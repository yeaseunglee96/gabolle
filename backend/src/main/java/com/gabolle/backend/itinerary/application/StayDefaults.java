package com.gabolle.backend.itinerary.application;

import java.util.Locale;
import java.util.Map;

/**
 * 갈래별 기본 체류 시간(분) — 한 곳에 모은다 (S15P21E201-1667, 2026-09-25 사용자 결정).
 *
 * <p>근거 자료가 없어 흔한 여행 계획 기준으로 잡은 값이다. 관광공사 「관람 소요 시간」은 문화시설 35곳 가운데 4곳에만 있고
 * 그중 시간으로 읽히는 값은 둘뿐이라 쓰지 않았다. 값을 바꾸려면 여기만 고친다.
 */
final class StayDefaults {

    /** 넘칠 때 체류를 줄여도 한 곳에 이만큼은 남긴다. */
    static final int MIN_STAY_MINUTES = 20;

    /** 갈래를 모르거나 아래 표에 없는 갈래. */
    static final int UNKNOWN_CATEGORY_MINUTES = 60;

    private static final Map<String, Integer> BY_CATEGORY = Map.of(
            "FOOD", 60,            // 밥집
            "CAFE_HEALING", 45,    // 카페·빵집 (디저트 표식만 있는 밥집도 조립 첫머리에서 카페로 읽힌다)
            "SEA_BEACH", 90,       // 바다
            "NATURE_WALK", 60,     // 걷기 길·공원
            "CULTURE_TEMPLE", 60,  // 문화
            "CITY", 60,            // 도시 — 시장·거리
            "FESTIVAL_EVENT", 90); // 축제·행사

    private StayDefaults() {
    }

    static int minutesFor(String category) {
        if (category == null) {
            return UNKNOWN_CATEGORY_MINUTES;
        }
        return BY_CATEGORY.getOrDefault(category.trim().toUpperCase(Locale.ROOT), UNKNOWN_CATEGORY_MINUTES);
    }
}
