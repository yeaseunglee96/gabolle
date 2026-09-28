package com.gabolle.backend.recommendation.application.port;

/**
 * 추천 → 일정 방향의 유일한 문. 추천 계층은 일정 도메인을 직접 import 하지 않고, 구현
 * ({@code itinerary.application.ItineraryDraftService})은 일정 쪽에 있다.
 *
 * {@link #assemble}·{@link #revise} 는 순수 계산과 읽기라 트랜잭션 밖에서 불리고,
 * {@link #persist}·{@link #publish} 는 {@code RecommendationRecorder} 의 바깥 트랜잭션 안에서
 * 불린다 — 거기서 실패하면 Job·후보·이벤트까지 함께 되돌려져 부분 반영이 남지 않는다.
 */
public interface ItineraryDraftPort {

    /**
     * 오늘 출발하는 당일치기를 너무 늦게 만들어 그날 넣을 시간이 없다 (S15P21E201-1734). 조립이 고장 난 것이 아니라
     * 사용자에게 알릴 사실이다 — 추천 작업은 {@code ITINERARY_NO_TIME_LEFT_TODAY} 로 끝난다.
     */
    final class NoTimeLeftTodayException extends RuntimeException {

        public NoTimeLeftTodayException(String message) {
            super(message);
        }
    }

    /** 새 일정을 처음부터 흩뿌린다. */
    ItineraryDraft assemble(ItineraryDraftCommand command);

    /**
     * 이 여행의 일정을 만들려면 장소가 몇 곳 필요한가 — 날 수 × 하루 항목 수 <b>× 여벌 배수</b>.
     *
     * <p>🔴 S15P21E201-1494 — <b>자리 수만으로는 모자란다.</b> 자리 수와 후보 수가 같으면
     * 전부 다 들어가야 하므로 <b>고를 여지가 없고</b>, 지역이 안 맞는 곳이 있어도 바꿔 넣을
     * 것이 없다. 배정이 지역을 보게 한 것({@code S15P21E201-1493})은 고를 것이 있을 때만
     * 뜻이 있다. 배수는 {@code gabolle.itinerary.candidate-headroom} 이 정한다.
     *
     * <p>🔴 <b>추천이 이것을 직접 계산하면 안 된다.</b> 하루 몇 곳인지는 여행의 「기분(pace)」이
     * 정하고 그 규칙은 구현({@code ItineraryDraftService.itemsPerDay})에 있다. 추천 쪽에 같은
     * 규칙을 한 벌 더 두면 한쪽만 바뀌고, 그 어긋남은 「일정이 왜 짧지」로만 나타난다.
     *
     * <p>왜 필요한가: 응답에 담을 개수({@code topK}, 기본 10)를 일정을 채우는 개수로도 쓰고
     * 있었다. 3일 × 하루 4곳 = 12자리인데 후보가 10개라, 밥집 상한에 걸려 몇 곳이 빠지면
     * 하루가 2~3곳으로 줄었다 (S15P21E201-1450 — 실측 3일 7곳).
     *
     * @return 1 이상. 여행을 못 찾으면 {@code IllegalStateException} 대신 1 을 준다 —
     *     이 값 때문에 추천이 실패하면 안 되고, 없는 여행은 뒤의 {@code assemble} 이 알린다
     */
    int placesNeeded(String tripId);

    /** 새 일정과 그 첫 판을 만든다. 바깥 트랜잭션 안. */
    ItineraryHandle persist(ItineraryDraft draft);

    /**
     * 있는 판의 하루만 다시 채운 초안을 만든다. 다른 날짜·고정 항목·이미 지나간 자리는
     * 보존하고, 빈 자리만 {@code rankedPool} 에서 제외 목록과 이미 배치된 장소를 뺀 것으로
     * 채운다. 후보가 없으면 그 자리는 빈 채로 두고 경고를 남긴다 — 조건을 완화해 억지로
     * 채우지 않는다.
     *
     * @throws IllegalStateException 바탕 판의 내용이 없거나, 기준 항목이 그 판에 없다
     */
    ItineraryRevisionDraft revise(ItineraryRevisionCommand command);

    /**
     * 초안을 새 판으로 게시한다. 바깥 트랜잭션 안.
     *
     * 판 번호 UNIQUE 와 포인터 조건부 UPDATE 가 "그 사이 아무도 판을 올리지 않았다" 를
     * 판정한다. 누가 올렸으면 {@link ItineraryPublishConflictException} 을 던지고, 바깥
     * 트랜잭션이 전부 되돌려져 이전 판이 그대로 최신으로 남는다.
     *
     * @throws ItineraryPublishConflictException 그 사이 다른 편집이 게시됐다
     */
    ItineraryHandle publish(ItineraryRevisionDraft draft);
}
