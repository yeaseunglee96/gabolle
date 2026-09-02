package com.gabolle.backend.event.domain;

/**
 * 이벤트 종류 — 열거값으로 못 박는다.
 *
 * <p>🔴 문자열로 두면 {@code "place_like"} · {@code "placeLike"} · {@code "좋아요"} 가
 * 다 들어오고 아무도 모른다. 열거형이면 컴파일 단계에서 막힌다.
 *
 * <p>🔴 <b>두 문서가 서로 다른 목록을 준다.</b> 여기서 합쳤다.
 * <ul>
 *   <li>API 명세 3.1 최소 이벤트 사전 — 12종</li>
 *   <li>S15P21E201-542 데이터 수집 명세 8.1 — M1 필수 5종 (앞 목록에 없는 4종 포함)</li>
 * </ul>
 * 합집합 16종을 두고 {@link #requiredForM1()} 로 가른다. 문서 불일치는 별도로 알린다.
 */
public enum EventType {

    // ── M1 필수 (S15P21E201-542 8.1) ───────────────────────────────
    /** 추천 요청 접수. 서버가 requestId 를 만든 시점 */
    RECOMMENDATION_REQUESTED(Producer.SERVER, true),
    /** 🔴 추천 카드가 실제로 화면에 보임. 목록에 들었다는 이유로 만들지 않는다 */
    RECOMMENDATION_IMPRESSION(Producer.CLIENT, true),
    /** 명시 선호 입력 */
    PREFERENCE_SET(Producer.SERVER, true),
    /** 제약 입력 (알레르기·식단·이동) */
    CONSTRAINT_SET(Producer.SERVER, true),
    /** 여행 생성 */
    TRIP_CREATED(Producer.SERVER, true),

    // ── 후속 마일스톤 (API 명세 3.1) ────────────────────────────────
    PLACE_VIEW(Producer.CLIENT, false),
    PLACE_LIKE(Producer.SERVER, false),
    PLACE_DISLIKE(Producer.SERVER, false),
    ITINERARY_LOCK(Producer.SERVER, false),
    ITINERARY_REMOVE(Producer.SERVER, false),
    ITINERARY_REPLACE(Producer.CLIENT, false),
    ROUTE_SKIP(Producer.CLIENT, false),
    PLACE_VISIT(Producer.SERVER, false),
    ROUTE_DEVIATION(Producer.CLIENT, false),
    EDITORIAL_PICK_PUBLISHED(Producer.SERVER, false),
    FEED_CANDIDATE_PRECOMPUTED(Producer.SERVER, false);

    private final Producer expectedProducer;
    private final boolean requiredForM1;

    EventType(Producer expectedProducer, boolean requiredForM1) {
        this.expectedProducer = expectedProducer;
        this.requiredForM1 = requiredForM1;
    }

    /**
     * 이 이벤트를 만들어야 하는 쪽 (DR-13).
     *
     * <p>실제 노출·상세 조회는 클라이언트가 보내고, 좋아요·일정 편집·방문 판정은
     * 서버 비즈니스 API 와 Outbox 가 만든다. 뒤바뀌면 신뢰할 수 없는 값이 들어온다 —
     * 클라이언트가 보내는 값은 조작될 수 있다.
     */
    public Producer expectedProducer() {
        return expectedProducer;
    }

    public boolean requiredForM1() {
        return requiredForM1;
    }

    /** 이 종류를 그 생산자가 보낼 수 있는가. */
    public boolean allowsProducer(Producer actual) {
        return expectedProducer == actual;
    }

    /** JSON 에 쓰는 소문자 이름. 예: {@code place_like} */
    public String wireName() {
        return name().toLowerCase();
    }
}
