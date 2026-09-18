package com.gabolle.backend.itinerary;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.itinerary.domain.ItineraryRun;

/**
 * 일정 진행 상태 — S15P21E201-1325 (시안 ⑤).
 *
 * <h2>이 검사가 막는 것</h2>
 * <b>멈췄는데 안 멈추는 것</b>과 <b>이미 다녀온 곳을 다시 가리키는 것</b>이다. 둘 다 화면에서는
 * 멀쩡해 보인다 — 중지해 놓고 기록이 쌓이면 「멈췄다」가 거짓이 되고, 이미 다녀온 곳을
 * 「지금 가는 중」이라고 말하면 사용자는 자기가 어디 있는지 화면에서 확인할 수 없게 된다.
 */
class ItineraryRunTest {

	private static final Instant T0 = Instant.parse("2026-10-03T09:00:00Z");

	private static final Instant T1 = Instant.parse("2026-10-03T09:30:00Z");

	private static ItineraryRun planned() {
		return ItineraryRun.planned("it-1", "trip-1", T0);
	}

	@Nested
	@DisplayName("출발과 중지")
	class StartAndPause {

		@Test
		@DisplayName("출발하면 달린다")
		void startsRunning() {
			assertThat(planned().start(T1).status()).isEqualTo(ItineraryRun.Status.RUNNING);
		}

		@Test
		@DisplayName("🔴 두 번 출발해도 처음 출발 시각을 지킨다 — 그 여행이 몇 시에 시작했는지 답할 수 있어야 한다")
		void keepsTheFirstStartedAt() {
			ItineraryRun once = planned().start(T0);

			ItineraryRun twice = once.start(T1);

			assertThat(twice.startedAt()).isEqualTo(T0);
		}

		@Test
		@DisplayName("중지하면 멈춘다")
		void pauses() {
			assertThat(planned().start(T0).pause(T1).status()).isEqualTo(ItineraryRun.Status.PAUSED);
		}

		@Test
		@DisplayName("🔴 다 돈 뒤에는 중지로 안 되돌린다 — 끝난 일정에 「출발」이 살아나면 안 된다")
		void doesNotRewindFromDone() {
			ItineraryRun done = planned().start(T0).advance(List.of(0, 1), 2, T0);
			assertThat(done.status()).isEqualTo(ItineraryRun.Status.DONE);

			assertThat(done.pause(T1).status()).isEqualTo(ItineraryRun.Status.DONE);
		}

		@Test
		@DisplayName("출발 전에는 중지할 것이 없다")
		void pausingBeforeStartDoesNothing() {
			assertThat(planned().pause(T1).status()).isEqualTo(ItineraryRun.Status.PLANNED);
		}
	}

	@Nested
	@DisplayName("다음으로 넘어간다")
	class Advance {

		@Test
		@DisplayName("마친 자리를 지나 다음으로 간다")
		void movesToTheNextStop() {
			ItineraryRun run = planned().start(T0).advance(List.of(0), 3, T1);

			assertThat(run.currentStopIndex()).isEqualTo(1);
			assertThat(run.status()).isEqualTo(ItineraryRun.Status.RUNNING);
		}

		/**
		 * 🔴 순서를 바꾸거나 늦게 온 신호 때문에 이미 다녀온 곳을 다시 가리키면, 화면이
		 * 「지금 가는 중」이라고 말하는 곳에 사용자는 <b>이미 다녀와 있다.</b>
		 */
		@Test
		@DisplayName("🔴 이미 마친 자리는 건너뛰며 앞으로 간다")
		void skipsOverAlreadySettledStops() {
			ItineraryRun run = planned().start(T0).advance(List.of(0, 1, 2), 5, T1);

			assertThat(run.currentStopIndex()).isEqualTo(3);
		}

		@Test
		@DisplayName("남은 것이 없으면 끝난다")
		void finishes() {
			ItineraryRun run = planned().start(T0).advance(List.of(0, 1), 2, T1);

			assertThat(run.status()).isEqualTo(ItineraryRun.Status.DONE);
			assertThat(run.currentStopIndex()).isEqualTo(2);
		}

		@Test
		@DisplayName("🔴 멈춰 있으면 안 움직인다 — 기록이 쌓이면 「멈췄다」가 거짓이 된다")
		void doesNotAdvanceWhilePaused() {
			ItineraryRun paused = planned().start(T0).pause(T0);

			assertThat(paused.advance(List.of(0), 3, T1)).isEqualTo(paused);
		}

		@Test
		@DisplayName("출발 전에도 안 움직인다")
		void doesNotAdvanceBeforeStart() {
			ItineraryRun before = planned();

			assertThat(before.advance(List.of(0), 3, T1)).isEqualTo(before);
		}
	}

	@Nested
	@DisplayName("위치를 언제 받나")
	class Location {

		/** 🔴 배터리와 개인정보 둘 다의 문제다. 멈춘 뒤에도 올리면 「중지」가 거짓이 된다. */
		@Test
		@DisplayName("🔴 달릴 때만 받는다")
		void onlyWhileRunning() {
			assertThat(planned().acceptsLocation()).isFalse();
			assertThat(planned().start(T0).acceptsLocation()).isTrue();
			assertThat(planned().start(T0).pause(T1).acceptsLocation()).isFalse();
			assertThat(planned().start(T0).advance(List.of(0), 1, T1).acceptsLocation()).isFalse();
		}
	}
}
