package com.gabolle.backend.itinerary.domain;

/**
 * 판 하나에 붙는 경고 — {@code itinerary_versions.warning_codes}.
 * {@code itinerary_item.warning_codes} 와 모양은 같지만 대상이 다르다. 항목 경고는 "이 장소에
 * 이런 문제가 있다" 이고 판 경고는 "이 판을 만들면서 이런 일이 있었다" 다. 빈 시간대처럼 담을
 * 항목 행 자체가 없는 사실이 여기로 온다 — 남은 다른 항목에 붙이면 그 장소에 문제가 있다고
 * 읽혀 거짓이 된다.
 * 표시 문구는 클라이언트 i18n 이다. 서버는 코드만 낸다.
 */
public final class ItineraryWarningCodes {

    /** 재계산했지만 채울 후보가 하나도 없었다. 그 시간대는 비어 있다. 조건을 완화하지 않았다. */
    public static final String RECALC_NO_CANDIDATE = "RECALC_NO_CANDIDATE";

    /** 일부만 채웠다. 후보가 자리보다 적었다. */
    public static final String RECALC_DAY_PARTIALLY_FILLED = "RECALC_DAY_PARTIALLY_FILLED";

    /**
     * 고정한 항목의 시각이 다시 배분됐다.
     * 고정은 "그 장소를 그대로 둔다"(존재·상대 순서)이고 "그 시각을 그대로 둔다" 는 뜻이 아니다 —
     * 하루의 활동 시간대를 그 날 항목 수로 균등 분할하므로 항목 수가 바뀌면 모든 시각이 움직인다.
     */
    public static final String RECALC_TIMES_RESHUFFLED = "RECALC_TIMES_RESHUFFLED";

    /**
     * 명소 자리를 채울 곳이 없어 비워 뒀다.
     * 밥집으로 메우면 명소 데이터가 모자란 지역에서 하루가 통째로 음식점이 되고, 모자라다는 사실이
     * 아무 데도 안 보인다. 사용자에게는 "이 앱은 밥집만 추천한다" 로 보이고 팀에게는 신호가 안 온다.
     */
    public static final String SIGHT_SLOT_UNFILLED = "SIGHT_SLOT_UNFILLED";

    private ItineraryWarningCodes() {
    }
}
