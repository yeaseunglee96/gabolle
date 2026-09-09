package com.gabolle.backend.place.domain;

/**
 * {@link PlaceFeature#indicatesPresence()} · {@link PlaceFeature#cannotRuleOutPresence()} 의
 * 판정식을 엔티티 밖으로 뽑은 것 (S15P21E201-604).
 *
 * <p>추천 엔진이 {@code PlaceCandidateResponse.Candidate}(엔티티가 아니라 조회 응답의
 * {@code PlaceFeatureView})를 놓고 같은 판정을 해야 하는데, 응답 쪽에는 {@link PlaceFeature}
 * 엔티티가 없다 — {@code evidenceStatus} 는 문자열, {@code value} 는 이미 파싱된 JSON 이다.
 * 판정식을 엔티티 메서드 안에 가둬 두면 그 경로에서는 다시 만들어야 하고, 그러면 두 판정이
 * 조용히 갈라질 여지가 생긴다. 그래서 이 순수 함수 하나를 두고 양쪽이 위임한다.
 *
 * <p>🔴 <b>판정 방향을 절대 뒤집지 마라.</b> 어제 배포를 막은 결함이 정확히 이 방향을
 * 뒤집은 것이었다 — 땅콩이 들었는지 확인 안 된 식당이 안전한 것처럼 후보에 들어갔다.
 *
 * <ul>
 * <li>{@link #indicatesPresence(String, String)} — "있다고 확인됨". {@code UNKNOWN} 이면
 *     {@code false} 다.</li>
 * <li>{@link #cannotRuleOutPresence(String, String)} — "없다고 확인되지 않음". {@code UNKNOWN}
 *     이면 <b>{@code true}</b> 다 — 모르는 것은 있는 것으로 취급해야 안전 제약이 뚫리지 않는다.</li>
 * </ul>
 */
public final class FeaturePresence {

	private FeaturePresence() {
	}

	/**
	 * 이 사실이 <b>"그 표식이 있다"</b> 는 근거가 되는가.
	 *
	 * <p>{@link PlaceFeature#indicatesPresence()} 와 판정이 완전히 같다 — {@code UNKNOWN} 은
	 * "모른다" 이지 "있다" 가 아니고, {@code VERIFIED}(또는 {@code ESTIMATED}) + 값
	 * {@code false} 는 "확인된 해당 없음" 이라 역시 "있다" 가 아니다.
	 *
	 * @param evidenceStatus {@link PlaceEvidenceStatus} 의 이름 문자열 ({@code VERIFIED} ·
	 *     {@code ESTIMATED} · {@code UNKNOWN})
	 * @param rawValue JSON 값의 원문. {@code UNKNOWN} 이면 항상 {@code null} 이어야 한다(DB CHECK
	 *     가 강제한다). JSON 리터럴 {@code false} <b>하나만</b> "확인된 해당 없음" 으로 읽는다 —
	 *     값 모양이 아직 확정되지 않았기 때문이다 ({@link PlaceFeature#indicatesPresence()} 참고)
	 */
	public static boolean indicatesPresence(String evidenceStatus, String rawValue) {
		return isConfirmed(evidenceStatus) && !isConfirmedAbsence(evidenceStatus, rawValue);
	}

	/**
	 * 없다고 <b>단정할 수 없는가</b>. 안전 제약을 거를 때 쓴다.
	 *
	 * <p>🔴 {@link #indicatesPresence(String, String)} 의 반대가 아니다. {@code UNKNOWN} 에서
	 * {@code indicatesPresence} 는 {@code false} 지만 이 메서드는 <b>{@code true}</b> 다 — 모르는
	 * 것을 있는 것으로 취급해야 알레르기 같은 안전 제약이 "모른다" 를 통과시키지 않는다.
	 * 확인된 부재({@code VERIFIED}/{@code ESTIMATED} + 값 {@code false})만 {@code false} 다.
	 */
	public static boolean cannotRuleOutPresence(String evidenceStatus, String rawValue) {
		return !isConfirmedAbsence(evidenceStatus, rawValue);
	}

	/**
	 * 원천이 실제로 확인해 준 상태인가.
	 *
	 * <p>🔴 <b>"UNKNOWN 이 아니다" 로 판정하지 않는다.</b> 엔티티에서는 {@link PlaceEvidenceStatus}
	 * 가 세 값뿐이라 그 둘이 같았지만, 여기 들어오는 것은 문자열이고 응답 계층이
	 * {@code NOT_COLLECTED}(아직 수집 대상에도 안 들어간 종류)를 하나 더 만들어 붙인다
	 * ({@code PlaceFeatureView.notCollected}). 부정으로 판정하면 그 값이
	 * <b>"있다고 확인됨" 으로 읽힌다</b> — 수집조차 안 한 식당이 필수 식단을 지원한다고
	 * 답하게 된다. 아는 값만 통과시키는 허용 목록으로 둔다.
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
