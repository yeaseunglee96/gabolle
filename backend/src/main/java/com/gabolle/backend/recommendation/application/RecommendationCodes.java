package com.gabolle.backend.recommendation.application;

import com.gabolle.backend.recommendation.domain.ConstraintSeverity;

/** 문자열로 흩어지면 오타가 조용히 통과하는 값들을 한군데 모은다. */
public final class RecommendationCodes {

	/**
	 * 이벤트 종류. S15P21E201-544 의 노출 이벤트가 이 요청과 짝을 이룬다.
	 *
	 * <p>P0 데이터 명세 8.1 이 이 이벤트의 필수 데이터로 <b>전체 버전</b>을 요구하므로,
	 * 버전을 구하기 전에 끝난 요청에는 이 이벤트를 만들 수 없다. 그런 요청은
	 * {@link #EVENT_RECOMMENDATION_FAILED} 로 남는다.
	 */
	public static final String EVENT_RECOMMENDATION_REQUESTED = "recommendation_requested";

	/**
	 * 추천이 실패했다. <b>모든</b> 실패에 대해 만들어진다.
	 *
	 * <p>🔴 이 이벤트가 왜 따로 있는가. {@code recommendation_requested} 는 버전 넷을 필수로
	 * 요구하는데, 엔진이 꺼져 있거나 버전을 못 받은 실패에는 그 값이 존재하지 않는다. 그래서
	 * 그런 요청은 이벤트 스트림에서 <b>통째로 사라졌고</b>, 실패율을 재면 서비스가 제일 안 좋았던
	 * 순간이 통계에서 빠졌다. 반쪽 envelope 을 내보내는 대신 버전을 요구하지 않는 이벤트를
	 * 하나 더 둔다.
	 *
	 * <p>🔴 생산자는 <b>BE Outbox</b> 다. 클라이언트 계측이 아니므로 FE·APP 은 손댈 것이 없다.
	 * 다만 이벤트 사전은 DATA 소유이므로(개발계획서 3.3, 9/3 12:00 고정) 소비자 쪽에 알려야 한다.
	 */
	public static final String EVENT_RECOMMENDATION_FAILED = "recommendation_failed";

	/** Outbox 의 aggregate 종류. */
	public static final String AGGREGATE_TYPE = "recommendation";

	/**
	 * 제약을 확인하지 못했다. 🔴 PASS 로 바꾼 것이 아니고, 기본 정책에서는 <b>제외하지도
	 * 않는다</b> — 경고만 붙여 내보낸다 (기능·화면 상세설계서 FR-REC-02).
	 */
	public static final String WARNING_CONSTRAINT_UNKNOWN = "CONSTRAINT_UNKNOWN";

	/**
	 * 🔴 확인하지 못한 제약의 등급이 기준선을 넘어 결과에서 뺐다.
	 * {@link #WARNING_CONSTRAINT_UNKNOWN} 과 함께 붙는다 — 그것은 "모른다", 이것은
	 * "몰라서 뺐다" 다. 어느 등급 때문이었는지는 {@link #warningForSeverity} 가 함께 남긴다.
	 */
	public static final String WARNING_UNKNOWN_CONSTRAINT_EXCLUDED = "UNKNOWN_CONSTRAINT_EXCLUDED";

	/**
	 * 어느 등급의 제약을 확인하지 못해 뺐는지. 제외율을 등급별로 셀 수 있어야 "데이터가
	 * 부족해서 빠진 것" 과 "정책이 빡빡해서 빠진 것" 을 나중에 구분할 수 있다.
	 */
	public static String warningForSeverity(ConstraintSeverity severity) {
		return "UNKNOWN_SEVERITY_" + severity.name();
	}

	/** 점수가 없어 순위를 매기지 못했다. 순위를 붙이는 대신 이 경고를 남긴다. */
	public static final String WARNING_SCORE_MISSING = "SCORE_MISSING";

	/**
	 * 보행 한도를 확인하지 못했다 (S15P21E201-555).
	 *
	 * <p>🔴 Editor's Pick 기준선에만 붙는다. {@code MAX_WALKING_METERS} 판정은 출발지에서의
	 * 거리를 봐야 하는데, Pick 은 편집자가 고른 코스라 "출발지에서 몇 m" 라는 값이 없다.
	 * 거리를 0 으로 채워 넣으면 한도를 설정한 사용자에게 <b>경고가 조용히 사라진다</b> —
	 * 통과했다는 뜻이 되기 때문이다. 그래서 판정을 건너뛰고 건너뛴 사실을 남긴다.
	 */
	public static final String WARNING_WALKING_LIMIT_NOT_CHECKED = "WALKING_LIMIT_NOT_CHECKED";

	/** 이 후보가 편집자가 고른 목록에서 왔다 (S15P21E201-555). */
	public static final String REASON_EDITORIAL_PICK = "EDITORIAL_PICK";

	/**
	 * 절대 기여 1위 축의 접두사 — {@code TOP_CONTRIBUTOR_DISTANCE} 처럼 붙는다
	 * (S15P21E201-548).
	 *
	 * <p>🔴 축 이름은 {@code score_components} 의 키를 <b>글자 그대로</b> 붙인다 — 그래서
	 * {@code TOP_CONTRIBUTOR_preferenceAlignment} 처럼 대문자 규칙이 깨져 보인다. 일부러
	 * 그렇게 뒀다. 대문자로 바꿔 적으면 코드에 적힌 축 이름과 {@code reasonRanking} 에
	 * 적힌 축 이름이 서로 달라지고, -205 가 문장을 만들 때 어느 쪽을 믿어야 하는지 알 수
	 * 없다. 되돌릴 수 있는 이름 하나가 예쁜 이름 둘보다 낫다.
	 */
	public static final String REASON_TOP_CONTRIBUTOR_PREFIX = "TOP_CONTRIBUTOR_";

	/**
	 * 다양성 재정렬로 순위가 <b>실제로 움직인</b> 후보에 붙는다 (S15P21E201-548).
	 *
	 * <p>🔴 재정렬을 돌렸다는 사실이 아니라 <b>이 후보의 자리가 바뀌었다</b>는 사실을
	 * 나타낸다. 돌렸어도 순서가 그대로인 후보에는 안 붙는다 — 붙이면 "왜 내려갔지" 를
	 * 물을 때 아무것도 걸러 주지 못한다.
	 */
	public static final String REASON_DIVERSITY_RERANKED = "DIVERSITY_RERANKED";

	/**
	 * 반환할 후보가 하나도 없다. GB-API-001 5장의 오류 코드이며 공개 API 는 <b>422</b> 로 낸다.
	 *
	 * <p>🔴 이때 하드 제약을 자동으로 완화해 억지로 결과를 만들지 않는다. 알레르기 조건을
	 * 슬쩍 풀어 채운 목록은, 빈 목록보다 나쁘다.
	 */
	public static final String ERROR_NO_FEASIBLE_RESULT = "RECOMMENDATION_NO_FEASIBLE_RESULT";

	/**
	 * 고른 갈래에 해당하는 장소가 반경 안에 하나도 없다 — S15P21E201-827.
	 *
	 * <p>{@link #ERROR_NO_FEASIBLE_RESULT} 와 다르다. 그쪽은 <b>후보를 만들었는데 제약에
	 * 전부 걸린</b> 경우이고, 이것은 <b>고를 후보가 애초에 없는</b> 경우다. 사용자에게 할
	 * 말이 다르다 — 앞의 것은 "조건을 좀 풀어 보시겠어요", 이것은 "그 갈래는 아직 준비가
	 * 안 됐어요" 다.
	 *
	 * <p>2026-09-10 배포에서 실제로 났다. 바다만 골랐는데 적재된 장소가 전부 음식점이라
	 * 후보가 0곳이었고, 그때 나간 코드는 {@code VERSION_UNRESOLVED} 였다 — 후보가 없어
	 * 수집분 이름을 못 정한 것이 원인이 아니라 <b>결과</b>인데, 그 결과가 코드가 됐다.
	 * 사용자도 우리도 그 코드에서는 이유를 못 읽는다.
	 *
	 * <p>다시 요청해도 달라지지 않는다. 자료가 들어와야 바뀐다.
	 */
	public static final String ERROR_NO_CANDIDATES = "ENGINE_NO_CANDIDATES";

	/** 엔진 호출 자체가 실패했다. */
	public static final String ERROR_ENGINE_UNAVAILABLE = "ENGINE_UNAVAILABLE";

	/**
	 * 🔴 이 배포에 {@code RecommendationEnginePort} 구현이 붙어 있지 않다.
	 *
	 * <p>이 코드가 있는 이유: 추천 엔진이 없다고 <b>애플리케이션 전체가 못 뜨면</b> 안 된다.
	 * 다른 도메인이 DB 를 켜려다 추천 때문에 막히는 일이 실제로 생겼다. 그래서 기동은 하되,
	 * 추천을 실제로 부르면 이 코드로 <b>시끄럽게</b> 실패하고 Job 이 FAILED 로 남는다.
	 */
	public static final String ERROR_ENGINE_NOT_CONFIGURED = "ENGINE_NOT_CONFIGURED";

	/** 후보에 일반 로그로 남길 수 없는 값이 섞여 있었다. 저장하지 않고 실패로 남긴다. */
	public static final String ERROR_SENSITIVE_DATA_REJECTED = "SENSITIVE_DATA_REJECTED";

	/** 후보를 저장 가능한 형태로 만들지 못했다. 예: 같은 place_id 가 두 번 왔다. */
	public static final String ERROR_CANDIDATE_ASSEMBLY_FAILED = "CANDIDATE_ASSEMBLY_FAILED";

	/**
	 * 🔴 S15P21E201-604 — 이 배포에 {@code ItineraryDraftPort} 구현이 붙어 있지 않다.
	 *
	 * <p>{@link #ERROR_ENGINE_NOT_CONFIGURED} 와 같은 이유다 — 일정 조립기가 없다고
	 * 애플리케이션 전체가 못 뜨면 안 된다. 그래서 기동은 하되, {@code ITINERARY_GENERATION}
	 * 을 실제로 성공시키려는 순간에만 이 코드로 시끄럽게 실패한다.
	 */
	public static final String ERROR_ITINERARY_PORT_NOT_CONFIGURED = "ITINERARY_PORT_NOT_CONFIGURED";

	/** 후보를 일정(항목·구간)으로 조립하지 못했다. {@code assemble()} 이 트랜잭션 밖에서 던진 것이다. */
	public static final String ERROR_ITINERARY_ASSEMBLY_FAILED = "ITINERARY_ASSEMBLY_FAILED";

	/** 조립까지는 됐지만 저장({@code persist()})이 실패했다. */
	public static final String ERROR_ITINERARY_PERSIST_FAILED = "ITINERARY_PERSIST_FAILED";

	/**
	 * 🔴 S15P21E201-249 — 재계산은 끝났는데 그 사이 다른 사람이 판을 올렸다. 결과를 버렸고 이전 판이
	 * 그대로 최신이다(FR-ITN-09). {@code retryable=true} — 사용자가 최신 일정을 불러와 다시 요청하면
	 * 된다. 최신 판 번호는 {@code GET /api/v1/itineraries/{id}} 로 다시 읽는다 — 폴링 응답의
	 * {@code failure.detail} 은 실패 단계 이름이라 거기 숫자를 섞지 않는다.
	 */
	public static final String ERROR_ITINERARY_VERSION_CONFLICT = "ITINERARY_VERSION_CONFLICT";

	/** 위 코드들로 분류되지 않는, 예상하지 못한 실패. {@code RecommendationJobWorker} 의 마지막 방어선이 쓴다. */
	public static final String ERROR_UNEXPECTED = "UNEXPECTED";

	private RecommendationCodes() {
	}
}
