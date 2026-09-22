package com.gabolle.backend.event.domain;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 이벤트 종류.
 *
 * <p>API 명세와 데이터 수집 명세가 서로 다른 목록을 주므로 여기서 합집합을 두고
 * {@link #requiredForM1()} 로 가른다.
 */
public enum EventType {

    // ── M1 필수 ────────────────────────────────────────────────────

    /**
     * 추천 요청 접수. 엔진을 부르기 전, 요청을 받은 즉시 발행한다 — 이 순서라야 엔진이
     * 꺼져 있던 시간대의 요청도 남고 실패율이 실제보다 좋게 나오지 않는다.
     *
     * <p>이 시점에는 서버가 아는 버전 셋(ontology · dataset · policy)만 담는다.
     */
    RECOMMENDATION_REQUESTED(Producer.SERVER, true, VersionRequirement.RECOMMENDATION,
            AggregateAxis.RECOMMENDATION_REQUEST),

    /** 추천 카드가 실제로 화면에 보임. 목록에 들었다는 이유로 만들지 않는다 */
    RECOMMENDATION_IMPRESSION(Producer.CLIENT, true, VersionRequirement.RECOMMENDATION,
            AggregateAxis.RECOMMENDATION_REQUEST),

    /**
     * 추천 요청이 실패함. 버전을 알기도 전에 죽는 경우가 있어(엔진이 꺼짐, 엔진이 버전을
     * 안 보냄) 버전 필수인 이벤트로는 만들 수 없다. 필수를 푸는 대신 전용 종류를 뒀다 —
     * 풀면 엔진이 정상일 때도 버전 없는 이벤트가 통과한다.
     *
     * <p>필수 필드: {@code request_id · job_id · trip_id · failure_code · failed_stage
     * · fallback_attempted · occurred_at}. 버전은 아는 것만 담는다.
     *
     * <p>{@code occurred_at}(실패 시각) 과 payload 의 {@code requested_at}(요청 시각) 을 둘 다
     * 담는다. 그 차이가 "얼마나 버티다 죽었는가" 이고 타임아웃 분석이 그 값을 쓴다.
     */
    RECOMMENDATION_FAILED(Producer.SERVER, true, VersionRequirement.BEST_EFFORT,
            AggregateAxis.RECOMMENDATION_REQUEST),

    /**
     * 명시 선호 입력. 축이 {@code TRIP} 이 아니라 {@code USER} 다 — 이것을 발행하는
     * {@code PUT /api/v1/me/preferences/spend} 는 계정 기본값을 바꾸는 경로라 tripId 가 없다.
     * 여행과의 관계는 실컬럼 {@code trip_id} 가 들고 있다.
     */
    PREFERENCE_SET(Producer.SERVER, true, VersionRequirement.NONE, AggregateAxis.USER),
    /** 제약 입력 (알레르기·식단·이동) */
    CONSTRAINT_SET(Producer.SERVER, true, VersionRequirement.NONE, AggregateAxis.TRIP),
    /** 여행 생성 */
    TRIP_CREATED(Producer.SERVER, true, VersionRequirement.NONE, AggregateAxis.TRIP),

    // ── 후속 마일스톤 ──────────────────────────────────────────────
    //
    // 아래 11종의 aggregate 축은 잠정이다. 그 이벤트를 실제로 구현할 때 확정한다.
    // 지금 확정할 수 없는 둘은 축을 비워 뒀다 — 비워 두면 쓰려는 순간 예외가 나서
    // 아무도 모르게 틀린 축으로 적히는 일이 없다.
    //
    // 장소 조회·저장·제외의 축이 USER 인 것은 홈 화면과 장소 상세가 여행 밖이라
    // 줄 tripId 가 없기 때문이다. 여행과의 관계는 trip_id 실컬럼이 그대로 들고 있다.
    PLACE_VIEW(Producer.CLIENT, false, VersionRequirement.NONE, AggregateAxis.USER),
    PLACE_LIKE(Producer.SERVER, false, VersionRequirement.NONE, AggregateAxis.USER,
            EnumSet.of(Producer.CLIENT, Producer.SERVER)),
    PLACE_DISLIKE(Producer.SERVER, false, VersionRequirement.NONE, AggregateAxis.USER,
            EnumSet.of(Producer.CLIENT, Producer.SERVER)),
    ITINERARY_LOCK(Producer.SERVER, false, VersionRequirement.NONE, AggregateAxis.TRIP),
    ITINERARY_REMOVE(Producer.SERVER, false, VersionRequirement.NONE, AggregateAxis.TRIP),
    ITINERARY_REPLACE(Producer.CLIENT, false, VersionRequirement.NONE, AggregateAxis.TRIP),
    ROUTE_SKIP(Producer.CLIENT, false, VersionRequirement.NONE, AggregateAxis.TRIP),
    PLACE_VISIT(Producer.SERVER, false, VersionRequirement.NONE, AggregateAxis.TRIP,
            EnumSet.of(Producer.CLIENT, Producer.SERVER)),
    ROUTE_DEVIATION(Producer.CLIENT, false, VersionRequirement.NONE, AggregateAxis.TRIP),

    // ── 글(story)에 달린 반응 ──────────────────────────────────────

    /**
     * 글에 좋아요를 눌렀다. 축은 누른 사람이고 글 번호는 payload 의 {@code storyId} 가 든다 —
     * 글을 축으로 삼으면 한 사람의 행동 이력을 한 줄로 읽을 수 없는데, 개인화가 읽는 것이
     * 그 줄이다.
     *
     * <p>{@code Producer.SERVER} 만 받는다. 앱이 직접 보낼 수 있게 두면 자기 글에는 못 단다는
     * 규칙을 피해 이벤트만 쌓을 수 있고, 그걸 세는 배치가 나중에 생긴다.
     */
    STORY_LIKE(Producer.SERVER, false, VersionRequirement.NONE, AggregateAxis.USER),

    /** 글에 싫어요를 눌렀다. DISLIKE 를 실제로 발행하는 유일한 자리다 */
    STORY_DISLIKE(Producer.SERVER, false, VersionRequirement.NONE, AggregateAxis.USER),

    /** 축 미정 — 여행에도 추천 요청에도 속하지 않는다. 편집 기획 단위가 필요하다 */
    EDITORIAL_PICK_PUBLISHED(Producer.SERVER, false, VersionRequirement.NONE, null),
    /** 축 미정 — 사전 계산 배치의 단위를 정해야 한다 */
    FEED_CANDIDATE_PRECOMPUTED(Producer.SERVER, false, VersionRequirement.NONE, null);

    /**
     * 개인화가 행동으로 보는 이벤트. 행동 기반 개인화를 껐을 때 적지 않을 것이자,
     * 끄는 순간 이미 적힌 것 중 지울 것이다. 두 곳이 같은 목록을 봐야 하므로 여기 한 번만 적는다.
     *
     * <p>빠진 것도 의도다. 명시 선호·제약·여행 생성은 사람이 직접 넣은 것이고,
     * 추천 요청·실패는 운영 기록이라 끊으면 개인화를 끈 사람의 장애를 조사할 수 없다.
     * 편집 추천과 사전 계산은 특정 사용자의 사건이 아니다.
     *
     * <p>{@link #TASTE_SIGNALS} 는 이 목록의 부분집합이다 — 세는 것은 좁고 안 모으는 것은 넓다.
     */
    private static final Set<EventType> BEHAVIOR_SIGNALS = EnumSet.of(
            PLACE_VIEW, PLACE_LIKE, PLACE_DISLIKE, PLACE_VISIT,
            ITINERARY_LOCK, ITINERARY_REMOVE, ITINERARY_REPLACE,
            ROUTE_SKIP, ROUTE_DEVIATION, RECOMMENDATION_IMPRESSION,
            STORY_LIKE, STORY_DISLIKE);

    /**
     * 취향 벡터가 세는 이벤트. 반드시 {@link #BEHAVIOR_SIGNALS} 의 부분집합이어야 한다 —
     * 세는 목록에 없다고 모아도 되는 것은 아니다.
     */
    private static final Set<EventType> TASTE_SIGNALS = EnumSet.of(
            PLACE_LIKE, PLACE_DISLIKE, PLACE_VISIT, PLACE_VIEW,
            ITINERARY_REMOVE, ITINERARY_REPLACE, ROUTE_SKIP);

    private final Producer expectedProducer;
    private final boolean requiredForM1;
    private final VersionRequirement versionRequirement;
    private final AggregateAxis aggregateAxis;
    private final Set<Producer> acceptedProducers;

    /** 만들어야 하는 쪽이 곧 보낼 수 있는 유일한 쪽인 이벤트 — 대부분이 여기 해당한다. */
    EventType(Producer expectedProducer, boolean requiredForM1, VersionRequirement versionRequirement,
              AggregateAxis aggregateAxis) {
        this(expectedProducer, requiredForM1, versionRequirement, aggregateAxis, EnumSet.of(expectedProducer));
    }

    EventType(Producer expectedProducer, boolean requiredForM1, VersionRequirement versionRequirement,
              AggregateAxis aggregateAxis, Set<Producer> acceptedProducers) {
        this.expectedProducer = expectedProducer;
        this.requiredForM1 = requiredForM1;
        this.versionRequirement = versionRequirement;
        this.aggregateAxis = aggregateAxis;
        this.acceptedProducers = Set.copyOf(acceptedProducers);
    }

    /**
     * 이 이벤트가 어느 대상에 붙는가 — {@code event_outbox.aggregate_type} · {@code aggregate_id}.
     * 같은 대상의 이벤트는 브로커로 나갈 때 순서가 지켜지고, 분석은 이 축으로 한 대상의
     * 이력을 한 줄로 읽는다.
     *
     * <p>{@code aggregate_id} 컬럼이 {@code UUID NOT NULL} 이라 실제로 UUID 를 갖고 있는 것만
     * 축이 될 수 있다. 장소는 UUID 가 payload 안에 있어 축으로 쓸 수 없다.
     */
    public enum AggregateAxis {

        /** 추천 요청 한 건. {@code aggregate_id = request_id} */
        RECOMMENDATION_REQUEST("recommendation"),

        /** 여행 한 건. {@code aggregate_id = trip_id} */
        TRIP("trip"),

        /**
         * 사용자 한 사람. {@code aggregate_id = user_id} — 수집 API 의 주체는 인증에서만
         * 읽으므로 언제나 있다. 홈·장소 상세에서 누른 저장은 여행 밖에서 일어나서,
         * 여행 축을 쓰면 넣을 값이 없어 이벤트가 통째로 버려진다.
         */
        USER("user");

        private final String type;

        AggregateAxis(String type) {
            this.type = type;
        }

        /** {@code event_outbox.aggregate_type} 에 들어가는 문자열. */
        public String type() {
            return type;
        }
    }

    /**
     * 버전을 얼마나 요구하는가.
     *
     * <p>버전 다섯의 출처가 다르다. ontology · dataset · policy 는 우리 서버가 알지만
     * model · feature 는 엔진이 알려주므로 엔진이 꺼지면 모른다. 그래서 "버전 전부 필수" 는
     * 지킬 수 없는 요구이고, {@code fallbackMode = BASELINE} 일 때 {@code modelVersion} 이
     * 없는 것은 결함이 아니라 정확한 사실이다.
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
         * 실제 판정은 {@code fallbackMode} 를 아는 recommendation 쪽 책임이고,
         * 여기서는 버전이 있어야 하는 이벤트라는 표시만 한다.
         */
        RECOMMENDATION,
        /** 아는 것만 담는다. 실패 이벤트 한정 예외 */
        BEST_EFFORT
    }

    /**
     * 이 이벤트를 만드는 것이 맞는 쪽. 누가 보낼 수 있는가가 아니다 — 실제로 받아 주는
     * 목록은 {@link #acceptedProducers()} 이고, 둘이 갈리는 종류가 있다.
     */
    public Producer expectedProducer() {
        return expectedProducer;
    }

    public boolean requiredForM1() {
        return requiredForM1;
    }

    /**
     * 이 이벤트가 행동 관찰인가. {@code true} 면 행동 기반 개인화를 끈 사람에게는 적지 않고,
     * 끄는 순간 이미 적힌 것도 지운다.
     */
    public boolean isBehaviorSignal() {
        return BEHAVIOR_SIGNALS.contains(this);
    }

    /** 행동 관찰 이벤트의 {@code event_type} 문자열 — 표를 직접 훑는 쪽(JPQL·JDBC)이 쓴다. */
    public static Set<String> behaviorSignalWireNames() {
        return wireNamesOf(BEHAVIOR_SIGNALS);
    }

    /** 이 이벤트를 취향 벡터가 세는가. 목록과 근거는 {@link #TASTE_SIGNALS}. */
    public boolean isTasteSignal() {
        return TASTE_SIGNALS.contains(this);
    }

    /**
     * 취향 신호의 {@code event_type} 문자열. 표에 들어가는 값은 {@link #wireName()} 이 만드는
     * 소문자이므로 이 목록을 손으로 다시 적지 않는다.
     */
    public static Set<String> tasteSignalWireNames() {
        return wireNamesOf(TASTE_SIGNALS);
    }

    private static Set<String> wireNamesOf(Set<EventType> types) {
        Set<String> names = new LinkedHashSet<>();
        for (EventType type : types) {
            names.add(type.wireName());
        }
        return names;
    }

    public VersionRequirement versionRequirement() {
        return versionRequirement;
    }

    /** 축이 정해져 있는가. {@code false} 면 아직 Outbox 에 적을 수 없다. */
    public boolean hasAggregateAxis() {
        return aggregateAxis != null;
    }

    public AggregateAxis aggregateAxis() {
        return aggregateAxis;
    }

    /**
     * {@code event_outbox.aggregate_type} 에 넣을 값. 축이 안 정해진 종류면 예외를 던진다 —
     * 기본값을 넣으면 그 행이 어느 대상의 것인지 영영 알 수 없는데 표는 멀쩡해 보인다.
     */
    public String aggregateType() {
        if (aggregateAxis == null) {
            throw new IllegalStateException(
                    this + " 의 aggregate 축이 아직 정해지지 않았다. 정하기 전에는 Outbox 에 적을 수 없다");
        }
        return aggregateAxis.type();
    }

    /**
     * 이 이벤트를 실제로 보내도 되는 쪽 전부. 대개 {@link #expectedProducer()} 하나뿐이고,
     * 둘인 종류는 {@code place_like} · {@code place_dislike} · {@code place_visit} 셋이다.
     */
    public Set<Producer> acceptedProducers() {
        return acceptedProducers;
    }

    /**
     * 이 종류를 그 생산자가 보낼 수 있는가.
     *
     * <p>저장·제외·방문은 서버 업무 API 가 만드는 것이 설계지만 그 API 가 아직 없어
     * 클라이언트에게도 열어 뒀다. 닫아 두면 앱이 보낸 것이 전부 거부되어 그 행동이 어디에도
     * 안 남는다. 신뢰 경계는 {@code event_outbox.producer} 칸이 그대로 들고 있고, 이벤트의
     * 주체는 인증에서만 읽으므로 클라이언트는 자기 행동밖에 못 만든다.
     */
    public boolean allowsProducer(Producer actual) {
        return acceptedProducers.contains(actual);
    }

    /** JSON 에 쓰는 소문자 이름. 예: {@code place_like} */
    public String wireName() {
        return name().toLowerCase();
    }

    /** 소문자 이름을 열거값으로 바꾼다. 모르는 이름은 거부한다. */
    public static EventType fromWireName(String wireName) {
        if (wireName == null || wireName.isBlank()) {
            throw new IllegalArgumentException("eventType 이 비어 있다");
        }
        try {
            return valueOf(wireName.trim().toUpperCase());
        }
        catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("모르는 이벤트 종류: " + wireName, ex);
        }
    }
}
