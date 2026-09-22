package com.gabolle.backend.batch;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.batch.application.TasteDecayProperties;
import com.gabolle.backend.batch.application.TasteDecayService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 감쇠 계수를 그대로 믿지 않는다 (S15P21E201-1501).
 *
 * <p>DB 없이 도는 시험이다 — 계수 판정은 질의를 보내기 <b>전</b>에 끝나므로
 * {@code JdbcTemplate} 이 없어도 된다. 그래서 {@code null} 을 넣는다. 질의까지 갔다면 그
 * {@code null} 때문에 터질 것이고, 그것도 이 시험이 잡는 셈이다.
 */
class TasteDecayGuardTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-23T00:00:00Z"), ZoneOffset.UTC);

	/**
	 * 🔴 조용히 고쳐 쓰지 않고 거부한다. 0 이하면 취향이 통째로 뒤집히거나 사라지고, 1 을
	 * 넘으면 옛 신호가 <b>커진다</b> — 둘 다 오류 없이 추천만 이상해지는 종류라 그 자리에서
	 * 막아야 한다.
	 */
	@Test
	@DisplayName("🔴 계수가 범위 밖이면 거부한다 — 조용히 고쳐 쓰지 않는다")
	void refusesFactorsOutsideTheRange() {
		for (double bad : new double[] { 0.0, -0.5, 1.5, Double.NaN }) {
			assertThatThrownBy(() -> service(bad).decayOnce())
				.as("계수 %s 를 받아들이면 안 된다", bad)
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("감쇠 계수");
		}
	}

	/**
	 * 1.0 은 「감쇠 안 함」이다. 질의를 돌리면 모든 행의 {@code updated_at} 만 바뀌어
	 * 「언제 마지막으로 값이 움직였나」를 못 보게 된다.
	 */
	@Test
	@DisplayName("계수가 1.0 이면 아무것도 안 한다 — updated_at 만 흔들지 않는다")
	void doesNothingWhenTheFactorIsOne() {
		assertThat(service(1.0).decayOnce()).isZero();
	}

	/** {@code JdbcTemplate} 이 {@code null} 이다 — 질의까지 가면 안 된다는 뜻이기도 하다. */
	private static TasteDecayService service(double dailyFactor) {
		TasteDecayProperties properties = new TasteDecayProperties();
		properties.setDailyFactor(dailyFactor);
		return new TasteDecayService(null, properties, CLOCK);
	}

}
