package com.gabolle.backend.itinerary.application;

/**
 * 예산을 넘겨도 되는 폭과 그 상한 — 한 곳에만 둔다.
 *
 * <p>일정을 짤 때({@link ItineraryDraftService} — 넣으면 상한을 넘는 곳은 안 앉힌다)와 일정을 보여 줄 때
 * ({@link ItineraryQueryService} — 응답의 {@code budgetCapKrw}, S15P21E201-1743)가 같은 값을 써야 한다. 앱은 합계가 예산을
 * 넘었어도 이 상한 안이면 경고 대신 부드럽게 알린다(사용자 결정 2026-09-26). 두 곳에서 따로 곱하면 반올림이 경계에서
 * 어긋나, 짤 때는 허용했는데 화면은 경고하는 일정이 생긴다.
 */
public final class BudgetAllowance {

	/**
	 * 예산을 넘겨도 되는 폭 — 예산의 20% (사용자 결정 2026-09-24, S15P21E201-1572). 예산은 딱 맞추기 어렵고(메뉴 값은
	 * 대표값이다) 조금 넘는 것은 괜찮지만, 두 배 가까이 넘는 일정(운영: 20만원에 23.8만원)을 그대로 내지는 않는다.
	 */
	public static final double RATIO = 0.20;

	private BudgetAllowance() {
	}

	/**
	 * 이 예산의 상한(원) — {@code 반올림(예산 × (1 + 폭))}.
	 *
	 * @return 예산이 없거나 0 이하면 {@code null} — 상한을 안 건다
	 */
	public static Integer capKrw(Integer budgetKrw) {
		if (budgetKrw == null || budgetKrw <= 0) {
			return null;
		}
		return (int) Math.round(budgetKrw * (1 + RATIO));
	}
}
