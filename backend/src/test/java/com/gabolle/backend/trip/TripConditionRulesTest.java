package com.gabolle.backend.trip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.trip.domain.TripConditionRules;
import com.gabolle.backend.trip.domain.TripConditionRules.Violation;

/**
 * 판정만 잰다 — DB 도 Spring 도 필요 없는 규칙이라 그것들 없이 부른다. 그 판정이 요청에서 400 과
 * 항목 이름으로 나가는지는 {@code TripConditionRevalidationFunctionalTest} 가 HTTP 로 잰다.
 */
class TripConditionRulesTest {

	private static final LocalDate START = LocalDate.of(2026, 10, 1);

	/** 성립하는 조건 한 벌. 각 검사는 여기서 한 칸만 어긋나게 바꿔 본다. */
	private static List<Violation> checkWith(LocalDate finish, Integer partySize, Integer budget,
			Double lat, Double lng, String window) {
		return TripConditionRules.check(START, finish, partySize, budget, lat, lng, window);
	}

	private static List<Violation> valid() {
		return checkWith(START.plusDays(2), 2, 100_000, 35.15, 129.16, "09:00-18:00");
	}

	@Test
	@DisplayName("모든 조건을 만족하면 어긴 것이 없다")
	void aValidSetHasNoViolations() {
		assertThat(valid()).isEmpty();
	}

	@Test
	@DisplayName("오는 날이 가는 날보다 앞이면 그 항목을 지목한다")
	void finishBeforeStartIsRejected() {
		List<Violation> violations = checkWith(START.minusDays(1), 2, 100_000, 35.15, 129.16, "09:00-18:00");

		assertThat(violations).extracting(Violation::field).containsExactly("finishDate");
		assertThat(violations.get(0).reason()).contains("앞이다");
	}

	@Test
	@DisplayName("최대 숙박 수를 넘으면 거부한다 — 경계는 통과한다")
	void tooManyNightsIsRejected() {
		assertThat(checkWith(START.plusDays(TripConditionRules.MAX_NIGHTS), 2, 100_000, 35.15, 129.16, null))
				.isEmpty();

		List<Violation> violations = checkWith(START.plusDays(TripConditionRules.MAX_NIGHTS + 1), 2, 100_000,
				35.15, 129.16, null);

		assertThat(violations).extracting(Violation::field).containsExactly("finishDate");
	}

	@Test
	@DisplayName("인원이 1명 미만이면 거부한다")
	void partySizeBelowOneIsRejected() {
		assertThat(checkWith(START.plusDays(2), 0, 100_000, 35.15, 129.16, null))
				.extracting(Violation::field).containsExactly("partySize");
	}

	@Test
	@DisplayName("예산이 최소값 미만이거나 단위가 어긋나면 거부한다")
	void budgetBelowMinimumOrOffUnitIsRejected() {
		assertThat(checkWith(START.plusDays(2), 2, TripConditionRules.BUDGET_MIN_KRW - 1, 35.15, 129.16, null))
				.extracting(Violation::field).containsExactly("budgetKrw");

		assertThat(checkWith(START.plusDays(2), 2, 15_500, 35.15, 129.16, null))
				.extracting(Violation::field).containsExactly("budgetKrw");

		// 경계 — 최소값 자체와 그 배수는 통과한다.
		assertThat(checkWith(START.plusDays(2), 2, TripConditionRules.BUDGET_MIN_KRW, 35.15, 129.16, null))
				.isEmpty();
	}

	@Test
	@DisplayName("예산을 안 보낸 것은 잘못 보낸 것과 다르다 — 통과한다")
	void anAbsentBudgetIsAllowed() {
		assertThat(checkWith(START.plusDays(2), 2, null, 35.15, 129.16, null)).isEmpty();
	}

	@Test
	@DisplayName("출발지 좌표가 없으면 거부한다 — 앱이 좌표를 실어 보내기 시작했다(S15P21E201-791, !479)")
	void missingOriginCoordinatesAreRejected() {
		assertThat(checkWith(START.plusDays(2), 2, 100_000, null, null, null))
				.extracting(Violation::field).containsExactly("originLat");
	}

	@Test
	@DisplayName("출발지 좌표는 하나만 있어도 거부한다 — 위도만으로는 아무 데도 못 가리킨다")
	void oneOriginCoordinateWithoutTheOtherIsRejected() {
		assertThat(checkWith(START.plusDays(2), 2, 100_000, 35.15, null, null))
				.extracting(Violation::field).containsExactly("originLat");

		assertThat(checkWith(START.plusDays(2), 2, 100_000, null, 129.16, null))
				.extracting(Violation::field).containsExactly("originLat");
	}

	@Test
	@DisplayName("출발지 좌표가 둘 다 있으면 통과한다")
	void bothOriginCoordinatesPresentIsAllowed() {
		assertThat(checkWith(START.plusDays(2), 2, 100_000, 35.15, 129.16, null)).isEmpty();
	}

	@Test
	@DisplayName("시간대 판정은 여기서 하지 않는다 — 그 자리가 자기 오류 코드로 답한다")
	void theTimeWindowIsJudgedElsewhere() {
		// 가로채면 같은 잘못에 다른 코드가 나가고, 그 코드를 보던 검사와 화면이 어긋난다.
		assertThat(checkWith(START.plusDays(2), 2, 100_000, 35.15, 129.16, "18:00-09:00")).isEmpty();
	}

	@Test
	@DisplayName("어긴 것이 여러 개면 전부 모아 준다 — 첫 번째에서 멈추지 않는다")
	void everyViolationIsCollected() {
		List<Violation> violations = checkWith(START.minusDays(1), 0, 15_500, 35.15, 129.16, "09:00-18:00");

		assertThat(violations).extracting(Violation::field)
				.containsExactlyInAnyOrder("finishDate", "partySize", "budgetKrw");
	}

	@Test
	@DisplayName("거부는 항목 이름이 붙은 줄로 나간다")
	void rejectionCarriesFieldLines() {
		assertThatThrownBy(() -> TripConditionRules.require(START, START.minusDays(1), 2, 100_000,
						35.15, 129.16, null))
				.isInstanceOf(TripConditionRules.TripConditionRejectedException.class)
				.hasMessageContaining("finishDate:");
	}

	@Test
	@DisplayName("성립하는 조건은 아무것도 던지지 않는다")
	void requireIsSilentForAValidSet() {
		TripConditionRules.require(START, START.plusDays(2), 2, 100_000, 35.15, 129.16, "09:00-18:00");
	}
}
