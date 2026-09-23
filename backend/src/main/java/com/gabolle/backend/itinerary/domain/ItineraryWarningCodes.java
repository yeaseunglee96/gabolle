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

    /**
     * 하루 안에 멀리 떨어진 곳이 섞였고, 그것을 바꿔 넣을 후보가 그 지역에 없었다 (S15P21E201-1493).
     *
     * <p>이동이 길어진 것을 <b>조용히 두지 않는다.</b> 2026-09-22 운영에서 영도 세 곳 사이에
     * 해운대 한 곳이 껴서 16km 를 갔다 돌아오는 일정이 나왔다(그 두 구간만 왕복 104분).
     *
     * <p>그 자체는 후보가 그 지역에 모자라서 생긴 일이라 코드가 늘 없앨 수는 없다. 억지로
     * 채우면 순위가 한참 낮은 곳을 넣게 되고, 그건 <b>이동을 줄이려고 추천 품질을 버리는</b>
     * 맞바꿈이다. 실측으로도 영도권 후보는 28곳뿐이고 그중 20곳이 음식점이라, 지역을 맞추려
     * 내려가다 보면 하루가 밥집이 된다.
     *
     * <p>그래서 바꿀 수 없으면 바꾸지 않고 <b>말한다.</b> 빈 자리를 밥집으로 메우지 않는 것
     * ({@link #SIGHT_SLOT_UNFILLED})과 같은 판단이다.
     */
    public static final String DAY_REGION_MIXED = "DAY_REGION_MIXED";

    private ItineraryWarningCodes() {
    }
}
