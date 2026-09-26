package com.gabolle.backend.itinerary.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 예산 상한(S15P21E201-1743) — 일정을 짤 때와 보여 줄 때가 이 한 곳에서 같은 값을 읽는다. */
class BudgetAllowanceTest {

	@Test
	@DisplayName("상한은 반올림(예산 × 1.2) — 10만 원이면 12만 원")
	void theCapIsTwentyPercentOver() {
		assertThat(BudgetAllowance.capKrw(100_000)).isEqualTo(120_000);
		assertThat(BudgetAllowance.capKrw(12_345)).isEqualTo(14_814);
	}

	@Test
	@DisplayName("예산을 안 정했거나 0 이하면 상한이 없다(null)")
	void noBudgetMeansNoCap() {
		assertThat(BudgetAllowance.capKrw(null)).isNull();
		assertThat(BudgetAllowance.capKrw(0)).isNull();
		assertThat(BudgetAllowance.capKrw(-1)).isNull();
	}
}
