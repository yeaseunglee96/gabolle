package com.gabolle.backend.recommendation.application.port;

/**
 * 추천 → 일정 방향의 유일한 문.
 *
 * <p>추천 계층은 일정 도메인을 직접 import 하지 않는다. 일정을 만들거나 고치는 일은 전부 이
 * 인터페이스를 통해 부르고, 구현({@code itinerary.application.ItineraryDraftService})은 일정
 * 쪽에 있다. 그래서 추천 슬라이스 테스트는 가짜 구현({@code FakeItineraryDraftPort})을 끼울 수
 * 있다.
 *
 * <h2>두 쌍의 메서드 — 트랜잭션 경계가 같다</h2>
 * <ul>
 *   <li>{@link #assemble}·{@link #revise} — <b>순수 계산 + 읽기.</b> 트랜잭션 밖에서 불린다.
 *       엔진 호출처럼 느릴 수 있는 일이 끝난 뒤 그 결과를 배치하는 단계다</li>
 *   <li>{@link #persist}·{@link #publish} — <b>바깥 트랜잭션 안</b>({@code RecommendationRecorder})
 *       에서 불린다. 여기서 실패하면 Job·후보·이벤트까지 함께 되돌려진다 — 그것이 "부분 반영
 *       금지"(FR-REC-09)의 DB 수준 보장이다</li>
 * </ul>
 */
public interface ItineraryDraftPort {

    /** 새 일정을 처음부터 흩뿌린다 — S15P21E201-604. */
    ItineraryDraft assemble(ItineraryDraftCommand command);

    /** 새 일정과 그 첫 판을 만든다. 바깥 트랜잭션 안. */
    ItineraryHandle persist(ItineraryDraft draft);

    /**
     * 있는 판의 하루만 다시 채운 초안을 만든다 — S15P21E201-249.
     *
     * <p>다른 날짜·고정 항목·이미 지나간 자리는 보존하고, 빈 자리만 {@code rankedPool} 에서
     * 제외 목록과 이미 배치된 장소를 뺀 것으로 채운다. 후보가 없으면 그 자리는 빈 채로 두고
     * 경고를 남긴다 — 조건을 완화해 억지로 채우지 않는다.
     *
     * @throws IllegalStateException 바탕 판의 내용이 없거나, 기준 항목이 그 판에 없다
     */
    ItineraryRevisionDraft revise(ItineraryRevisionCommand command);

    /**
     * 초안을 새 판으로 게시한다. 바깥 트랜잭션 안.
     *
     * <p>🔴 여기가 FR-ITN-09 의 CAS 다 — 판 번호 UNIQUE 와 포인터 조건부 UPDATE 가 "그 사이
     * 아무도 판을 올리지 않았다" 를 판정한다. 누가 올렸으면
     * {@link ItineraryPublishConflictException} 을 던지고, 바깥 트랜잭션이 전부 되돌려져
     * 이전 판이 그대로 최신으로 남는다.
     *
     * @throws ItineraryPublishConflictException 그 사이 다른 편집이 게시됐다
     */
    ItineraryHandle publish(ItineraryRevisionDraft draft);
}
