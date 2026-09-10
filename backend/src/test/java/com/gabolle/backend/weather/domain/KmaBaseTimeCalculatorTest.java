package com.gabolle.backend.weather.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link KmaBaseTimeCalculator} 검증 — S15P21E201-366.
 *
 * <p>하루 8회(02·05·08·11·14·17·20·23시) 발표, 발표 후 10분 지나야 반영이 끝난다는 규칙을
 * 여러 현재시각 케이스로 잰다.
 */
class KmaBaseTimeCalculatorTest {

	private static final LocalDate DAY = LocalDate.of(2026, 9, 10);

	@Test
	@DisplayName("발표시각+10분을 막 지났으면 그 회차를 쓴다")
	void justAfterAvailabilityDelayUsesThatSlot() {
		KmaBaseTime result = KmaBaseTimeCalculator.calculate(LocalDateTime.of(DAY, LocalTime.of(8, 10)));

		assertThat(result.baseDate()).isEqualTo(DAY);
		assertThat(result.baseTime()).isEqualTo(LocalTime.of(8, 0));
	}

	@Test
	@DisplayName("🔴 발표시각+10분이 아직 안 지났으면 그 이전 회차를 쓴다")
	void beforeAvailabilityDelayUsesThePreviousSlot() {
		// 08:05 -- 08:00 회차는 아직(08:10 이후에야 반영), 그래서 05:00 회차를 써야 한다.
		KmaBaseTime result = KmaBaseTimeCalculator.calculate(LocalDateTime.of(DAY, LocalTime.of(8, 5)));

		assertThat(result.baseDate()).isEqualTo(DAY);
		assertThat(result.baseTime()).isEqualTo(LocalTime.of(5, 0));
	}

	@Test
	@DisplayName("발표시각 정각은 아직 10분이 안 지났으므로 이전 회차를 쓴다")
	void exactAnnounceTimeIsNotYetAvailable() {
		KmaBaseTime result = KmaBaseTimeCalculator.calculate(LocalDateTime.of(DAY, LocalTime.of(11, 0)));

		assertThat(result.baseTime()).isEqualTo(LocalTime.of(8, 0));
	}

	@Test
	@DisplayName("자정 직후(02:10 이전)는 전날 23:00 회차를 쓴다")
	void justAfterMidnightUsesPreviousDaysLastSlot() {
		KmaBaseTime result = KmaBaseTimeCalculator.calculate(LocalDateTime.of(DAY, LocalTime.of(1, 0)));

		assertThat(result.baseDate()).isEqualTo(DAY.minusDays(1));
		assertThat(result.baseTime()).isEqualTo(LocalTime.of(23, 0));
	}

	@Test
	@DisplayName("하루의 마지막 회차(23:00)도 10분 지나야 그날 것으로 잡는다")
	void lastSlotOfDayNeedsDelayToo() {
		KmaBaseTime beforeDelay = KmaBaseTimeCalculator.calculate(LocalDateTime.of(DAY, LocalTime.of(23, 5)));
		assertThat(beforeDelay.baseDate()).isEqualTo(DAY);
		assertThat(beforeDelay.baseTime()).isEqualTo(LocalTime.of(20, 0));

		KmaBaseTime afterDelay = KmaBaseTimeCalculator.calculate(LocalDateTime.of(DAY, LocalTime.of(23, 10)));
		assertThat(afterDelay.baseDate()).isEqualTo(DAY);
		assertThat(afterDelay.baseTime()).isEqualTo(LocalTime.of(23, 0));
	}
}
