package com.gabolle.backend.trip.infra;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 여행 돈의 경계값(S15P21E201-1935, AI 리뷰 !1985 의 제안). DB 의 CHECK 와 같은 범위를 저장하기 전에 막는지 —
 * DB 없이 돈다. 어긋나면 DB 가 500 으로 거부하고 화면은 이유를 모른다.
 */
class TripExpenseLimitsTest {

	@Test
	@DisplayName("쓴 돈은 1원~1억 원 — 0원·1억 1원은 저장하기 전에 400")
	void expenseAmount() {
		assertThat(TripExpenseJpaEntity.requireAmount(1)).isEqualTo(1);
		assertThat(TripExpenseJpaEntity.requireAmount(100_000_000)).isEqualTo(100_000_000);
		assertThatThrownBy(() -> TripExpenseJpaEntity.requireAmount(0)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> TripExpenseJpaEntity.requireAmount(100_000_001)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("예산은 1원~10억 원")
	void budgetAmount() {
		assertThat(TripBudgetJpaEntity.requireAmount(1_000_000_000)).isEqualTo(1_000_000_000);
		assertThatThrownBy(() -> TripBudgetJpaEntity.requireAmount(0)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> TripBudgetJpaEntity.requireAmount(-5)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("갈래는 정한 일곱 중 하나 · 장소 이름은 120자까지, 공백뿐이면 비운다")
	void categoryAndText() {
		assertThat(TripExpenseJpaEntity.requireCategory("FOOD")).isEqualTo("FOOD");
		assertThatThrownBy(() -> TripExpenseJpaEntity.requireCategory("food")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> TripExpenseJpaEntity.requireCategory(null)).isInstanceOf(IllegalArgumentException.class);
		assertThat(TripExpenseJpaEntity.trimToLimit("   ", 120, "장소 이름")).isNull();
		assertThat(TripExpenseJpaEntity.trimToLimit(" 밀면집 ", 120, "장소 이름")).isEqualTo("밀면집");
		assertThatThrownBy(() -> TripExpenseJpaEntity.trimToLimit("가".repeat(121), 120, "장소 이름")).isInstanceOf(IllegalArgumentException.class);
	}
}
