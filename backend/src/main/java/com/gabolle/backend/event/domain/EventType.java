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
 *   <li>{@link #RECOMMENDATION_FAILED} — 2026-09-02 DATA 결정으로 추가 (아래 참고)</li>
 * </ul>
 * 합집합 17종을 두고 {@link #requiredForM1()} 로 가른다.
 */
public enum EventType {

    // ── M1 필수 (S15P21E201-542 8.1) ───────────────────────────────

    /**
     * 추천 요청 접수.
     *
     * <p>🔴 <b>엔진을 부르기 전, 요청을 받은 즉시 발행한다.</b>
     * 이 순서가 중요하다 — 엔진이 꺼져 있던 시간대의 요청이 이벤트에 안 남으면
     * 분석에서 <b>"아예 없었던 것"</b> 처럼 보이고, 실패율이 실제보다 좋게 나온다.
     * 서비스가 제일 안 좋았던 순간이 통계에서 사라진다.
     *
     * <p>이 시점에는 서버가 아는 버전 셋(ontology · dataset · policy)만 담는다.
     */
    RECOMMENDATION_REQUESTED(Producer.SERVER, true, VersionRequirement.RECOMMENDATION),

    /** 🔴 추천 카드가 실제로 화면에 보임. 목록에 들었다는 이유로 만들지 않는다 */
    RECOMMENDATION_IMPRESSION(Producer.CLIENT, true, VersionRequirement.RECOMMENDATION),

    /**
     * 추천 요청이 실패함 — 2026-09-02 DATA 결정으로 추가.
     *
     * <p><b>왜 별도 이벤트인가.</b> 버전을 알기도 전에 죽는 경우가 있다(엔진이 꺼짐,
     * 엔진이 버전을 안 보냄). 그때는 "버전 필수" 인 이벤트를 만들 수 없다.
     *
     * <p>대안은 둘이었다.
     * <ol>
     *   <li>이벤트 규칙에서 "버전 필수" 를 "있으면 넣기" 로 완화</li>
     *   <li>실패 전용 이벤트를 하나 추가 ← <b>이것을 골랐다</b></li>
     * </ol>
     *
     * <p>🔴 1번을 안 고른 이유 — 필수를 풀면 <b>엔진이 정상일 때도 버전 없는 이벤트가
     * 통과한다.</b> NFR-08 재현성이 통째로 약해진다. 예외를 만들려고 규칙을 없애는 것이다.
     *
     * <p>2번의 단점("목록에 항목이 하나 늘어난다")은 사실상 없다 —
     * {@code producer = SERVER} 라서 <b>FE·APP 은 계측할 것이 없다</b>(DR-13).
     *
     * <p>필수 필드: {@code request_id · job_id · trip_id · failure_code · failed_stage
     * · fallback_attempted · occurred_at}. 버전은 <b>아는 것만</b> 담는다.
     */
    RECOMMENDATION_FAILED(Producer.SERVER, true, VersionRequirement.BEST_EFFORT),

    /** 명시 선호 입력 */
    PREFERENCE_SET(Producer.SERVER, true, VersionRequirement.NONE),
    /** 제약 입력 (알레르기·식단·이동) */
    CONSTRAINT_SET(Producer.SERVER, true, VersionRequirement.NONE),
    /** 여행 생성 */
    TRIP_CREATED(Producer.SERVER, true, VersionRequirement.NONE),

    // ── 후속 마일스톤 (API 명세 3.1) ────────────────────────────────
    PLACE_VIEW(Producer.CLIENT, false, VersionRequirement.NONE),
    PLACE_LIKE(Producer.SERVER, false, VersionRequirement.NONE),
    PLACE_DISLIKE(Producer.SERVER, false, VersionRequirement.NONE),
    ITINERARY_LOCK(Producer.SERVER, false, VersionRequirement.NONE),
    ITINERARY_REMOVE(Producer.SERVER, false, VersionRequirement.NONE),
    ITINERARY_REPLACE(Producer.CLIENT, false, VersionRequirement.NONE),
    ROUTE_SKIP(Producer.CLIENT, false, VersionRequirement.NONE),
    PLACE_VISIT(Producer.SERVER, false, VersionRequirement.NONE),
    ROUTE_DEVIATION(Producer.CLIENT, false, VersionRequirement.NONE),
    EDITORIAL_PICK_PUBLISHED(Producer.SERVER, false, VersionRequirement.NONE),
    FEED_CANDIDATE_PRECOMPUTED(Producer.SERVER, false, VersionRequirement.NONE);

    private final Producer expectedProducer;
    private final boolean requiredForM1;
    private final VersionRequirement versionRequirement;

    EventType(Producer expectedProducer, boolean requiredForM1, VersionRequirement versionRequirement) {
        this.expectedProducer = expectedProducer;
        this.requiredForM1 = requiredForM1;
        this.versionRequirement = versionRequirement;
    }

    /**
     * 버전을 얼마나 요구하는가.
     *
     * <p>🔴 버전 다섯이 <b>한 덩어리가 아니다.</b> 출처가 다르다.
     * <table>
     *   <tr><th>버전</th><th>누가 아는가</th><th>엔진이 꺼져도</th></tr>
     *   <tr><td>ontology · dataset · policy</td><td>우리 서버</td><td>안다</td></tr>
     *   <tr><td>model · feature</td><td>🔴 엔진이 알려준다</td><td>모른다</td></tr>
     * </table>
     *
     * <p>그래서 "버전 전부 필수" 는 애초에 지킬 수 없는 요구다.
     * {@code fallbackMode = BASELINE} 이면 모델을 안 쓴 것이고,
     * 그때 {@code modelVersion} 이 없는 것은 결함이 아니라 <b>정확한 사실</b>이다.
     */
    public enum VersionRequirement {
        /** 버전이 필요 없다. 추천과 무관한 이벤트 */
        NONE,
        /**
         * 추천 이벤트 — {@code fallbackMode} 가 요구 버전을 결정한다.
         * <pre>
         * MODEL     → model · feature · ontology · dataset · policy
         * RULE      → ontology · dataset · policy
         * BASELINE  → dataset · policy
         * </pre>
         * 🔴 실제 판정은 {@code fallbackMode} 를 아는 recommendation 쪽 책임이다.
         * 여기서는 "버전이 있어야 하는 이벤트" 라는 표시만 한다.
         */
        RECOMMENDATION,
        /** 🔴 아는 것만 담는다. 실패 이벤트 한정 예외 */
        BEST_EFFORT
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

    public VersionRequirement versionRequirement() {
        return versionRequirement;
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
