package com.gabolle.backend.recommendation.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.recommendation.domain.ConstraintVerdict;
import com.gabolle.backend.trip.domain.Trip;

/**
 * 총예산이 추천 순위를 실제로 움직이는가.
 *
 * <p>이 시험이 생긴 이유. 온보딩에서 총예산은 <b>「필수」</b>인데 — 건너뛸 수 없고 최소
 * 1만원·1만원 단위 검증까지 붙어 있는데 — 추천과 일정에서 그 값을 참조하는 곳이 <b>0건</b>
 * 이었다. 반드시 답하게 해 놓고 아무 일도 안 했다.
 */
class BudgetFitTest {

	private static final UUID CHEAP = new UUID(9L, 1L);

	private static final UUID PRICEY = new UUID(9L, 2L);

	private static final UUID UNKNOWN_PRICE = new UUID(9L, 3L);

	@Test
	@DisplayName("1인 1끼 예산이 등급을 정한다 — 1만/2만/4만이 경계다")
	void perMealBudgetDecidesTheBand() {
		// 2명 · 3일 · 하루 2끼 = 12끼. 경계가 1인 1끼 기준이라 총액은 그 12배다.
		assertThat(BudgetFit.targetBand(trip(120_000))).as("1인 1끼 10,000원").isEqualTo("LOW");
		assertThat(BudgetFit.targetBand(trip(240_000))).as("1인 1끼 20,000원").isEqualTo("MID");
		assertThat(BudgetFit.targetBand(trip(480_000))).as("1인 1끼 40,000원").isEqualTo("MID_HIGH");
		assertThat(BudgetFit.targetBand(trip(600_000))).as("1인 1끼 50,000원").isEqualTo("HIGH");
	}

	@Test
	@DisplayName("예산을 안 받은 여행은 아무것도 하지 않는다 — 0 은 「0원으로 간다」가 아니라 「안 골랐다」다")
	void noBudgetMeansNoAdjustment() {
		assertThat(BudgetFit.targetBand(trip(null))).isNull();
		assertThat(BudgetFit.targetBand(trip(0))).isNull();

		List<EngineCandidate> in = List.of(candidate(CHEAP, 0.5));
		assertThat(BudgetFit.apply(in, Map.of(CHEAP, "LOW"), null)).isSameAs(in);
	}

	@Test
	@DisplayName("🔴 예산 안에 들면 올리고 넘으면 내린다 — 비싼 곳도 목록에는 남는다")
	void withinBudgetGoesUpOverBudgetGoesDown() {
		List<EngineCandidate> out = BudgetFit.apply(
				List.of(candidate(CHEAP, 0.50), candidate(PRICEY, 0.50)),
				Map.of(CHEAP, "LOW", PRICEY, "HIGH"), "MID");

		assertThat(out.get(0).preRankScore()).isEqualTo(0.50 + BudgetFit.MATCH_BONUS);
		assertThat(out.get(0).reasonCodes()).contains(BudgetFit.REASON_WITHIN_BUDGET);

		assertThat(out.get(1).preRankScore()).isEqualTo(0.50 - BudgetFit.OVER_PENALTY);
		assertThat(out.get(1).reasonCodes()).contains(BudgetFit.REASON_OVER_BUDGET);

		assertThat(out).as("점수만 움직이고 후보를 빼지 않는다").hasSize(2);
	}

	@Test
	@DisplayName("🔴 가격대를 모르는 곳은 가감이 없다 — 「모른다」를 「비싸다」로 치면 조사 안 된 곳이 통째로 밀린다")
	void unknownPriceIsNotPunished() {
		EngineCandidate before = candidate(UNKNOWN_PRICE, 0.50);

		List<EngineCandidate> out = BudgetFit.apply(List.of(before), Map.of(CHEAP, "LOW"), "MID");

		assertThat(out.get(0)).isSameAs(before);
		assertThat(out.get(0).reasonCodes()).doesNotContain(BudgetFit.REASON_OVER_BUDGET);
	}

	@Test
	@DisplayName("같은 등급은 예산 안이다 — 경계에 걸친 곳을 벌주지 않는다")
	void sameBandCountsAsWithinBudget() {
		List<EngineCandidate> out = BudgetFit.apply(
				List.of(candidate(CHEAP, 0.50)), Map.of(CHEAP, "MID"), "MID");

		assertThat(out.get(0).reasonCodes()).contains(BudgetFit.REASON_WITHIN_BUDGET);
	}

	@Test
	@DisplayName("점수가 없는 후보는 점수 없이 남는다 — 없는 것과 낮은 것은 다르다")
	void nullScoreStaysNull() {
		List<EngineCandidate> out = BudgetFit.apply(
				List.of(candidate(CHEAP, null)), Map.of(CHEAP, "LOW"), "MID");

		assertThat(out.get(0).preRankScore()).isNull();
		assertThat(out.get(0).reasonCodes()).contains(BudgetFit.REASON_WITHIN_BUDGET);
	}

	@Test
	@DisplayName("🔴 예산만 바꾸면 순위가 뒤집힌다 — 이것이 「총예산이 산다」의 뜻이다")
	void budgetAloneFlipsTheOrder() {
		// 비싼 곳이 원래 점수가 더 높다. 예산이 넉넉하면 그대로 1등이고, 빠듯하면 밀린다.
		List<EngineCandidate> pool = List.of(candidate(PRICEY, 0.60), candidate(CHEAP, 0.55));
		Map<UUID, String> bands = Map.of(PRICEY, "HIGH", CHEAP, "LOW");

		assertThat(topOf(BudgetFit.apply(pool, bands, "HIGH")))
				.as("예산이 넉넉하면 원래 점수대로 비싼 곳이 앞이다").isEqualTo(PRICEY);
		assertThat(topOf(BudgetFit.apply(pool, bands, "LOW")))
				.as("예산이 빠듯하면 싼 곳이 앞이다").isEqualTo(CHEAP);
	}

	// ── 도구 ──────────────────────────────────────────────────────────────────

	private static UUID topOf(List<EngineCandidate> candidates) {
		return candidates.stream()
				.max((a, b) -> Double.compare(a.preRankScore(), b.preRankScore()))
				.orElseThrow()
				.placeId();
	}

	/** 2명 · 3일 여행. 1인 1끼 = 총예산 ÷ 2 ÷ 3 ÷ 2. */
	private static Trip trip(Integer budgetKrw) {
		return Trip.builder()
				.tripId("trp_1").createdBy("usr_1")
				.startDate(LocalDate.of(2026, 10, 1)).finishDate(LocalDate.of(2026, 10, 3))
				.partySize(2).budgetKrw(budgetKrw).timezone("Asia/Seoul")
				.createdAt(Instant.now())
				.build();
	}

	private static EngineCandidate candidate(UUID placeId, Double score) {
		return new EngineCandidate(placeId, "BASELINE", ConstraintVerdict.PASS, List.of(), List.of(), null,
				Map.of(), Map.of("distance", 0.1), score, List.of("NEAR_ORIGIN"), List.of());
	}
}
