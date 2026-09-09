package com.gabolle.backend.recommendation.domain;

/**
 * 제약 하나를 못 지켰을 때 얼마나 위험한가의 등급.
 *
 * <p>온톨로지가 제약마다 함께 돌려주는 값이다(온톨로지 명세서의 제약 속성
 * {@code type · severity · operator · threshold}). 백엔드가 정하지 않는다.
 *
 * <p>🔴 <b>왜 불리언이 아닌가.</b> "확인 못 했다(UNKNOWN)" 는 흔하게 나올 수밖에 없다 —
 * 장소 데이터에는 늘 구멍이 있다. 불리언이면 그 흔한 미확인 전체에 대해 <b>막느냐 통과시키느냐를
 * 한 번에</b> 정해야 한다. 전부 막으면 후보가 말라붙고, 전부 통과시키면 알레르기까지 흘러나간다.
 *
 * <p>등급이면 <b>위험한 소수만 막고, 데이터가 채워지는 대로 기준선을 옮길 수 있다</b> —
 * 코드를 고치지 않고 설정 한 줄로.
 *
 * <p>🔴 이름은 REC/AI 확인이 필요하다. {@link #REQUIRED} 는 GB-API-001 INT-ONT-01 이
 * 실제로 쓰는 말이고("REQUIRED 의 UNKNOWN 은 경고/정책 처리"), 나머지 둘은 SRS 가 제약을
 * 나누는 층(FR-PLN-03 안전·식단 <i>절대 제외 조건</i> / FR-PLN-04 이동 <i>선호</i>)에서
 * 가져왔다. 온톨로지 판정 계약이 확정되면 이 이름들을 맞춘다.
 */
public enum ConstraintSeverity {

	/**
	 * 못 지키면 안 되는 것. 알레르기·필수 식단/할랄·검증된 접근 불가.
	 * 개발계획서 9.1 이 "어떤 이유로도 자르지 않는 것" 으로 못 박은 층이다.
	 */
	REQUIRED(3),

	/** 지키면 좋은 것. 경사·계단·그늘·조용함 같은 이동·분위기 선호. */
	PREFERRED(2),

	/** 있으면 참고하는 것. 못 지켜도 여행이 망가지지 않는다. */
	OPTIONAL(1);

	private final int rank;

	ConstraintSeverity(int rank) {
		this.rank = rank;
	}

	/** 클수록 위험하다. 기준선과 비교할 때만 쓴다. */
	int rank() {
		return this.rank;
	}

	/**
	 * 둘 중 더 위험한 쪽. {@code null} 은 "없음" 으로 보고 반대쪽을 돌려준다.
	 *
	 * <p>바깥에서 {@link #rank()} 를 직접 비교하지 않게 하려고 둔다 — 선언 순서와 rank 가
	 * 반대 방향이라 밖에서 비교하면 조용히 뒤집힌다.
	 */
	public static ConstraintSeverity moreSevere(ConstraintSeverity left, ConstraintSeverity right) {
		if (left == null) {
			return right;
		}
		if (right == null) {
			return left;
		}
		return (left.rank >= right.rank) ? left : right;
	}

	/**
	 * 온톨로지가 보낸 문자열을 등급으로 읽는다.
	 *
	 * @return 아는 등급이면 그 값, 비었거나 모르는 이름이면 {@code null} — 부르는 쪽이
	 *     "등급을 못 받았을 때 무엇으로 볼지" 를 설정에 따라 정한다
	 */
	public static ConstraintSeverity parseOrNull(Object raw) {
		if (raw == null) {
			return null;
		}
		String name = raw.toString().trim().toUpperCase();
		for (ConstraintSeverity severity : values()) {
			if (severity.name().equals(name)) {
				return severity;
			}
		}
		return null;
	}
}
