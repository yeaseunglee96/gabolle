package com.gabolle.backend.place.domain;

/**
 * {@link PlaceFeature#indicatesPresence()} · {@link PlaceFeature#cannotRuleOutPresence()} 의
 * 판정식. 엔티티와 조회 응답({@code PlaceFeatureView}) 양쪽이 같은 판정을 해야 하는데 응답 쪽에는
 * 엔티티가 없어서, 순수 함수 하나를 두고 양쪽이 위임한다.
 *
 * <p>판정 방향을 뒤집지 마라. {@link #indicatesPresence(String, String)} 은 "있다고 확인됨" 이라
 * {@code UNKNOWN} 이면 {@code false} 이고, {@link #cannotRuleOutPresence(String, String)} 은
 * "없다고 확인되지 않음" 이라 {@code UNKNOWN} 이면 {@code true} 다 — 모르는 것을 있는 것으로
 * 취급해야 안전 제약이 뚫리지 않는다.
 */
public final class FeaturePresence {

	private FeaturePresence() {
	}

	/**
	 * 이 사실이 "그 표식이 있다" 는 근거가 되는가. {@code UNKNOWN} 은 "있다" 가 아니고,
	 * 확인된 상태 + 값 {@code false} 는 "확인된 해당 없음" 이라 역시 "있다" 가 아니다.
	 *
	 * @param evidenceStatus {@link PlaceEvidenceStatus} 의 이름 문자열
	 * @param rawValue JSON 값의 원문. JSON 리터럴 {@code false} 하나만 "확인된 해당 없음" 으로
	 *     읽는다 — 값 모양이 아직 확정되지 않았기 때문이다
	 */
	public static boolean indicatesPresence(String evidenceStatus, String rawValue) {
		return isConfirmed(evidenceStatus) && !isConfirmedAbsence(evidenceStatus, rawValue);
	}

	/**
	 * 없다고 단정할 수 없는가. 안전 제약을 거를 때 쓴다.
	 * {@link #indicatesPresence(String, String)} 의 반대가 아니다 — {@code UNKNOWN} 에서 이 메서드는
	 * {@code true} 이고, 확인된 부재만 {@code false} 다.
	 */
	public static boolean cannotRuleOutPresence(String evidenceStatus, String rawValue) {
		return !isConfirmedAbsence(evidenceStatus, rawValue);
	}

	/**
	 * 원천이 실제로 확인해 준 상태인가. "UNKNOWN 이 아니다" 로 판정하지 않는다 — 여기 들어오는 것은
	 * 문자열이고 응답 계층이 {@code NOT_COLLECTED} 를 하나 더 붙인다. 부정으로 판정하면 수집조차
	 * 안 한 값이 "있다고 확인됨" 으로 읽힌다. 아는 값만 통과시키는 허용 목록으로 둔다.
	 */
	private static boolean isConfirmed(String evidenceStatus) {
		return PlaceEvidenceStatus.VERIFIED.name().equals(evidenceStatus)
				|| PlaceEvidenceStatus.ESTIMATED.name().equals(evidenceStatus);
	}

	/** 확인했고 결과가 "아니다" 인가. */
	private static boolean isConfirmedAbsence(String evidenceStatus, String rawValue) {
		return isConfirmed(evidenceStatus) && rawValue != null && "false".equals(rawValue.trim());
	}
}
