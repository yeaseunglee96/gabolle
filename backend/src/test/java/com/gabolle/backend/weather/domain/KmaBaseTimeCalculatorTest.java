package com.gabolle.backend.weather.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 하루 8회(02·05·08·11·14·17·20·23시) 발표, 발표 후 10분이 지나야 반영이 끝난다는 규칙을
 * 여러 현재시각으로 잰다.
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
	@DisplayName("🔴 한 회차 앞으로 간다 — 23시 발표에 오늘이 없을 때 20시 발표를 찾아가는 길")
	void previousStepsBackOneAnnouncement() {
		KmaBaseTime at2300 = new KmaBaseTime(DAY, LocalTime.of(23, 0));

		KmaBaseTime at2000 = KmaBaseTimeCalculator.previous(at2300);

		assertThat(at2000.baseDate()).isEqualTo(DAY);
		assertThat(at2000.baseTime()).isEqualTo(LocalTime.of(20, 0));
	}

	@Test
	@DisplayName("하루 첫 회차(02:00)에서 앞으로 가면 전날 마지막 회차(23:00)다")
	void previousCrossesMidnight() {
		KmaBaseTime previous = KmaBaseTimeCalculator.previous(new KmaBaseTime(DAY, LocalTime.of(2, 0)));

		assertThat(previous.baseDate()).isEqualTo(DAY.minusDays(1));
		assertThat(previous.baseTime()).isEqualTo(LocalTime.of(23, 0));
	}

	@Test
	@DisplayName("🔴 발표 회차가 아닌 시각이 오면 지어내지 않고 멈춘다")
	void previousRejectsATimeThatIsNotAnAnnouncement() {
		assertThatThrownBy(() -> KmaBaseTimeCalculator.previous(new KmaBaseTime(DAY, LocalTime.of(21, 0))))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("하루 발표 횟수는 여덟 — 거슬러 볼 수 있는 한계가 그것이다")
	void announcementsPerDayIsEight() {
		assertThat(KmaBaseTimeCalculator.announcementsPerDay()).isEqualTo(8);
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
