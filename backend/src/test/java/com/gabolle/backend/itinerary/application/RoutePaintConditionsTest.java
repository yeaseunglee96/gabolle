package com.gabolle.backend.itinerary.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.trip.domain.PersonalizationScope;
import com.gabolle.backend.trip.domain.PreferenceSnapshot;
import com.gabolle.backend.trip.domain.PreferenceSnapshot.AnswerStatus;
import com.gabolle.backend.trip.domain.PreferenceSnapshot.PreferenceAnswer;

/**
 * 코스 지도가 경로 선을 무엇으로 칠할지 정하는 「사용자가 고른 조건」 — S15P21E201-1895.
 *
 * <p>지도는 <b>고른 것만</b> 칠한다. 안 골랐거나 「상관없어요」인데 칠하면 조건과 무관한 색이 된다.
 */
class RoutePaintConditionsTest {

	@Test
	@DisplayName("경사 「피하고 싶어요」를 골랐으면 slopeAvoid 이고, 그늘은 안 골랐으니 false 다")
	void slopeAvoidOnly() {
		RoutePaintConditions c = RoutePaintConditions.from(snapshot(selected("SLOPE_PREFERENCE", "\"AVOID\"")));

		assertThat(c.slopeAvoid()).isTrue();
		assertThat(c.shadePrefer()).isFalse();
	}

	@Test
	@DisplayName("경사 + 그늘을 함께 골랐으면 둘 다 true — 지도가 합친 점수로 칠한다")
	void bothChosen() {
		RoutePaintConditions c = RoutePaintConditions.from(snapshot(selected("SLOPE_PREFERENCE", "\"AVOID\""),
				selected("SHADE_PREFERENCE", "\"PREFER\"")));

		assertThat(c).isEqualTo(new RoutePaintConditions(true, true));
	}

	@Test
	@DisplayName("🔴 「상관없어요」는 고른 것이 아니다 — 칠하지 않는다")
	void noPreferenceAnswersAreNotChoices() {
		RoutePaintConditions c = RoutePaintConditions.from(snapshot(selected("SLOPE_PREFERENCE", "\"ALLOW\""),
				selected("SHADE_PREFERENCE", "\"NO_PREFERENCE\"")));

		assertThat(c).isEqualTo(RoutePaintConditions.NONE);
	}

	@Test
	@DisplayName("🔴 건너뛰었거나 모르는 답은 고른 것이 아니다 — 값이 있어 보여도 SELECTED 가 아니면 false")
	void skippedOrUnknownIsNotChosen() {
		RoutePaintConditions c = RoutePaintConditions.from(snapshot(
				new PreferenceAnswer("SLOPE_PREFERENCE", null, AnswerStatus.SKIPPED),
				new PreferenceAnswer("SHADE_PREFERENCE", null, AnswerStatus.UNKNOWN)));

		assertThat(c).isEqualTo(RoutePaintConditions.NONE);
	}

	@Test
	@DisplayName("선호를 남기지 않은 여행(판 없음)은 아무것도 안 골랐다")
	void noSnapshot() {
		assertThat(RoutePaintConditions.from(null)).isEqualTo(RoutePaintConditions.NONE);
		assertThat(RoutePaintConditions.from(snapshot())).isEqualTo(RoutePaintConditions.NONE);
	}

	@Test
	@DisplayName("옛 앱의 5단계 슬라이더 답도 읽는다 — 경사 1·2단계는 피함, 그늘 4·5단계는 우선")
	void legacySliderAnswers() {
		// 슬라이더 1~5 는 0~1 로 옮겨져(1→0.0, 2→0.25, 3→0.5, 4→0.75, 5→1.0) 경계 0.25·0.75 로 갈린다.
		assertThat(RoutePaintConditions.from(snapshot(selected("SLOPE_PREFERENCE", "1"))).slopeAvoid()).isTrue();
		assertThat(RoutePaintConditions.from(snapshot(selected("SLOPE_PREFERENCE", "2"))).slopeAvoid()).isTrue();
		assertThat(RoutePaintConditions.from(snapshot(selected("SLOPE_PREFERENCE", "3"))).slopeAvoid()).isFalse();
		assertThat(RoutePaintConditions.from(snapshot(selected("SHADE_PREFERENCE", "4"))).shadePrefer()).isTrue();
		assertThat(RoutePaintConditions.from(snapshot(selected("SHADE_PREFERENCE", "5"))).shadePrefer()).isTrue();
		assertThat(RoutePaintConditions.from(snapshot(selected("SHADE_PREFERENCE", "3"))).shadePrefer()).isFalse();
	}

	@Test
	@DisplayName("모르는 낱말이나 깨진 값은 false 다 — 모르는 것을 골랐다고 지어내지 않는다")
	void unknownWordsAreNotChosen() {
		RoutePaintConditions c = RoutePaintConditions.from(snapshot(selected("SLOPE_PREFERENCE", "\"MAYBE\""),
				selected("SHADE_PREFERENCE", "not json")));

		assertThat(c).isEqualTo(RoutePaintConditions.NONE);
	}

	@Test
	@DisplayName("경사·그늘 말고 다른 차원의 답은 무시한다")
	void otherDimensionsAreIgnored() {
		RoutePaintConditions c = RoutePaintConditions.from(snapshot(selected("QUIETNESS", "1"),
				selected("LOCALITY", "\"AVOID\"")));

		assertThat(c).isEqualTo(RoutePaintConditions.NONE);
	}

	private static PreferenceAnswer selected(String dimension, String valueJson) {
		return new PreferenceAnswer(dimension, valueJson, AnswerStatus.SELECTED);
	}

	private static PreferenceSnapshot snapshot(PreferenceAnswer... answers) {
		return new PreferenceSnapshot("snap-1", "trip-1", 1, new ArrayList<>(List.of(answers)),
				PersonalizationScope.TRIP, List.of(), Instant.parse("2026-09-30T00:00:00Z"));
	}
}
