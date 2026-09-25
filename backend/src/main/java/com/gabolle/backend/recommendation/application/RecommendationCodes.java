package com.gabolle.backend.recommendation.application;

import com.gabolle.backend.recommendation.domain.ConstraintSeverity;

/** 문자열로 흩어지면 오타가 조용히 통과하는 값들을 한군데 모은다. */
public final class RecommendationCodes {

	/**
	 * 이벤트 종류. 이 이벤트는 전체 버전을 필수 데이터로 요구하므로, 버전을 구하기 전에 끝난
	 * 요청에는 만들 수 없다 — 그런 요청은 {@link #EVENT_RECOMMENDATION_FAILED} 로 남는다.
	 */
	public static final String EVENT_RECOMMENDATION_REQUESTED = "recommendation_requested";

	/**
	 * 추천이 실패했다. 모든 실패에 대해 만들어진다.
	 *
	 * <p>{@code recommendation_requested} 와 따로 있는 이유는 그쪽이 버전 넷을 필수로 요구하기
	 * 때문이다. 엔진이 꺼져 있거나 버전을 못 받은 실패에는 그 값이 없어서, 그런 요청이 이벤트
	 * 스트림에서 통째로 사라진다. 반쪽 envelope 대신 버전을 요구하지 않는 이벤트를 하나 더 둔다.
	 *
	 * <p>생산자는 BE Outbox 다. 클라이언트 계측이 아니므로 FE·APP 은 손댈 것이 없다.
	 */
	public static final String EVENT_RECOMMENDATION_FAILED = "recommendation_failed";

	/** Outbox 의 aggregate 종류. */
	public static final String AGGREGATE_TYPE = "recommendation";

	/**
	 * 제약을 확인하지 못했다. PASS 로 바꾼 것이 아니고, 기본 정책에서는 제외하지도 않는다 —
	 * 경고만 붙여 내보낸다.
	 */
	public static final String WARNING_CONSTRAINT_UNKNOWN = "CONSTRAINT_UNKNOWN";

	/**
	 * 확인하지 못한 제약의 등급이 기준선을 넘어 결과에서 뺐다.
	 * {@link #WARNING_CONSTRAINT_UNKNOWN} 과 함께 붙는다 — 그것은 "모른다", 이것은 "몰라서
	 * 뺐다" 다. 어느 등급 때문이었는지는 {@link #warningForSeverity} 가 함께 남긴다.
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
	 * 보행 한도를 확인하지 못했다. Editor's Pick 기준선에만 붙는다 —
	 * {@code MAX_WALKING_METERS} 판정은 출발지에서의 거리를 봐야 하는데 Pick 에는 그 값이 없다.
	 * 거리를 0 으로 채우면 통과했다는 뜻이 되어 경고가 조용히 사라지므로, 판정을 건너뛰고
	 * 건너뛴 사실을 남긴다.
	 */
	public static final String WARNING_WALKING_LIMIT_NOT_CHECKED = "WALKING_LIMIT_NOT_CHECKED";

	/**
	 * 접근성을 안 재 봤다는 경고. "못 간다" 가 아니라 "모른다" 다 —
	 * {@code ACCESS_VERIFIED_UNAVAILABLE}(재 보고 안 된다고 나온 곳)과 다른 사실이라 자리를
	 * 따로 둔다.
	 *
	 * <p>값을 만드는 곳과 세는 곳이 서로 다른 갈래에 있어서 이 상수를 여기 둔다. 양쪽이 각자
	 * 문자열을 적으면 한쪽만 고쳐지는 날 경고가 조용히 0건이 된다 — 세는 쪽이 못 찾을 뿐
	 * 오류는 안 난다.
	 */
	public static final String WARNING_ACCESSIBILITY_UNVERIFIED = "ACCESSIBILITY_UNVERIFIED";

	/**
	 * 이 식단을 지원하는지 <b>안 재 봤다</b>는 경고. 「지원 안 한다」가 아니다 —
	 * {@code DIET_NOT_SUPPORTED}(확인된 위반)와 다른 사실이고, 화면 문구도 다르다.
	 *
	 * <p>🔴 <b>문자열을 바꾸지 말 것.</b> 앱의 경고 사전(`warningLabels.ts`)이 이 이름으로
	 * 들어가 있고({@code S15P21E201-1503}), 그 사전에 없는 코드는 화면에서 <b>조용히
	 * 사라진다</b>({@code describeWarningCodes} 가 모르는 코드를 건너뛴다). 이름만 바꿔도
	 * 경고가 안 뜨는데 오류는 안 납니다.
	 */
	public static final String WARNING_DIET_SUPPORT_UNVERIFIED = "DIET_SUPPORT_UNVERIFIED";

	/** 이 후보가 편집자가 고른 목록에서 왔다. */
	public static final String REASON_EDITORIAL_PICK = "EDITORIAL_PICK";

	/**
	 * 절대 기여 1위 축의 접두사 — {@code TOP_CONTRIBUTOR_DISTANCE} 처럼 붙는다.
	 *
	 * <p>축 이름은 {@code score_components} 의 키를 글자 그대로 붙이므로
	 * {@code TOP_CONTRIBUTOR_preferenceAlignment} 처럼 대문자 규칙이 깨져 보인다. 일부러
	 * 그렇게 뒀다 — 대문자로 바꿔 적으면 코드의 축 이름과 {@code reasonRanking} 의 축 이름이
	 * 서로 달라져 어느 쪽을 믿어야 하는지 알 수 없다.
	 */
	public static final String REASON_TOP_CONTRIBUTOR_PREFIX = "TOP_CONTRIBUTOR_";

	/**
	 * 다양성 재정렬로 순위가 실제로 움직인 후보에 붙는다. 재정렬을 돌렸다는 사실이 아니라 이
	 * 후보의 자리가 바뀌었다는 사실이다 — 돌렸어도 순서가 그대로인 후보에는 안 붙는다.
	 */
	public static final String REASON_DIVERSITY_RERANKED = "DIVERSITY_RERANKED";

	/**
	 * 반환할 후보가 하나도 없다. 공개 API 는 422 로 낸다. 이때 하드 제약을 자동으로 완화해
	 * 억지로 결과를 만들지 않는다 — 알레르기 조건을 슬쩍 풀어 채운 목록은 빈 목록보다 나쁘다.
	 */
	public static final String ERROR_NO_FEASIBLE_RESULT = "RECOMMENDATION_NO_FEASIBLE_RESULT";

	/**
	 * 고른 갈래에 해당하는 장소가 반경 안에 하나도 없다.
	 *
	 * <p>{@link #ERROR_NO_FEASIBLE_RESULT} 와 다르다. 그쪽은 후보를 만들었는데 제약에 전부
	 * 걸린 경우이고 이것은 고를 후보가 애초에 없는 경우다 — 사용자에게 할 말이 다르다.
	 * 다시 요청해도 달라지지 않고 자료가 들어와야 바뀐다.
	 */
	public static final String ERROR_NO_CANDIDATES = "ENGINE_NO_CANDIDATES";

	/** 엔진 호출 자체가 실패했다. */
	public static final String ERROR_ENGINE_UNAVAILABLE = "ENGINE_UNAVAILABLE";

	/**
	 * 이 배포에 {@code RecommendationEnginePort} 구현이 붙어 있지 않다. 추천 엔진이 없다고
	 * 애플리케이션 전체가 못 뜨면 안 되므로 기동은 하되, 추천을 실제로 부르면 이 코드로
	 * 시끄럽게 실패하고 Job 이 FAILED 로 남는다.
	 */
	public static final String ERROR_ENGINE_NOT_CONFIGURED = "ENGINE_NOT_CONFIGURED";

	/** 후보에 일반 로그로 남길 수 없는 값이 섞여 있었다. 저장하지 않고 실패로 남긴다. */
	public static final String ERROR_SENSITIVE_DATA_REJECTED = "SENSITIVE_DATA_REJECTED";

	/** 후보를 저장 가능한 형태로 만들지 못했다. 예: 같은 place_id 가 두 번 왔다. */
	public static final String ERROR_CANDIDATE_ASSEMBLY_FAILED = "CANDIDATE_ASSEMBLY_FAILED";

	/**
	 * 이 배포에 {@code ItineraryDraftPort} 구현이 붙어 있지 않다.
	 * {@link #ERROR_ENGINE_NOT_CONFIGURED} 와 같은 이유로 기동은 하되,
	 * {@code ITINERARY_GENERATION} 을 실제로 성공시키려는 순간에만 시끄럽게 실패한다.
	 */
	public static final String ERROR_ITINERARY_PORT_NOT_CONFIGURED = "ITINERARY_PORT_NOT_CONFIGURED";

	/** 후보를 일정(항목·구간)으로 조립하지 못했다. {@code assemble()} 이 트랜잭션 밖에서 던진 것이다. */
	public static final String ERROR_ITINERARY_ASSEMBLY_FAILED = "ITINERARY_ASSEMBLY_FAILED";

	/** 조립까지는 됐지만 저장({@code persist()})이 실패했다. */
	public static final String ERROR_ITINERARY_PERSIST_FAILED = "ITINERARY_PERSIST_FAILED";

	/**
	 * 재계산은 끝났는데 그 사이 다른 사람이 판을 올렸다. 결과를 버렸고 이전 판이 그대로
	 * 최신이다. {@code retryable=true} — 사용자가 최신 일정을 불러와 다시 요청하면 된다.
	 * 최신 판 번호는 일정 조회로 다시 읽는다 — 폴링 응답의 {@code failure.detail} 은 실패
	 * 단계 이름이라 거기 숫자를 섞지 않는다.
	 */
	public static final String ERROR_ITINERARY_VERSION_CONFLICT = "ITINERARY_VERSION_CONFLICT";

	/** 위 코드들로 분류되지 않는, 예상하지 못한 실패. {@code RecommendationJobWorker} 의 마지막 방어선이 쓴다. */
	public static final String ERROR_UNEXPECTED = "UNEXPECTED";

	/**
	 * 추천 실행기가 꽉 차(도는 것 + 줄이 다 참) 작업을 못 받았다 — 시작도 안 했다. 잠시 뒤 같은 요청이면 되므로
	 * 다시 시도할 수 있다. 응답의 오류 코드도 같은 글자다 (S15P21E201-1685).
	 */
	public static final String ERROR_SERVER_BUSY = "SERVER_BUSY";

	private RecommendationCodes() {
	}
}
