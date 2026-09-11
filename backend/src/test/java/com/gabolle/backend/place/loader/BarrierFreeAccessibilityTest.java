package com.gabolle.backend.place.loader;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 무장애 문장 → 접근성 표식 — S15P21E201-331.
 *
 * <p>이 판정이 느슨하면 휠체어를 쓰는 사람이 못 들어가는 곳을 추천받는다. 추천에서 하드
 * 필터로 쓰이는 값이라 틀린 표식이 취향 불일치가 아니라 <b>문 앞에서 돌아서는 일</b>이 된다.
 *
 * <p>문장은 2026-09-11 수집본에서 그대로 옮겼다.
 */
class BarrierFreeAccessibilityTest {

	@Test
	@DisplayName("원천이 휠체어 접근 가능이라고 쓴 문장에만 붙인다")
	void wheelchairOnlyWhenTheSourceSaysSo() {
		assertThat(BarrierFreeAccessibility.of("주출입구는 턱이 없어 휠체어 접근 가능함_무장애 편의시설", ""))
				.containsExactly("WHEELCHAIR");
		assertThat(BarrierFreeAccessibility.of("주출입구는 경사로가 있어 휠체어 접근 가능함", ""))
				.containsExactly("WHEELCHAIR");
	}

	@Test
	@DisplayName("문 모양만 적힌 것은 접근성 이야기가 아니다")
	void doorShapeIsNotAnAccessibilityClaim() {
		assertThat(BarrierFreeAccessibility.of("여닫이문", "")).isEmpty();
		assertThat(BarrierFreeAccessibility.of("자동문", "")).isEmpty();
	}

	@Test
	@DisplayName("주출입구에 계단이 있다고 적힌 곳은 안 붙는다 — 이 한 줄이 판정을 좁게 만든 이유다")
	void mainEntranceWithStairsGetsNothing() {
		assertThat(BarrierFreeAccessibility.of("주출입구는 계단이 있고 보조출입구는 무단차임", ""))
				.as("보조출입구가 무단차라는 말에 이끌려 붙이면 사람을 계단 앞에 보낸다")
				.isEmpty();
	}

	@Test
	@DisplayName("뜻이 같아도 문구가 다르면 안 붙는다 — 모름으로 남는 손해가 더 작다")
	void sameMeaningDifferentWordsIsLeftUnknown() {
		assertThat(BarrierFreeAccessibility.of("높낮이 차가 없어 휠체어를 이용하여 출입가능", "")).isEmpty();
		assertThat(BarrierFreeAccessibility.of("주출입구에 경사로 설치되어 있음", "")).isEmpty();
	}

	@Test
	@DisplayName("유모차 칸은 값이 있으면 붙인다")
	void strollerWhenTheFieldHasAValue() {
		assertThat(BarrierFreeAccessibility.of("", "대여가능")).containsExactly("STROLLER");
		assertThat(BarrierFreeAccessibility.of("", "유모차 있음")).containsExactly("STROLLER");
	}

	@Test
	@DisplayName("둘 다 해당하면 둘 다 붙는다")
	void bothCodesCanApply() {
		assertThat(BarrierFreeAccessibility.of("주출입구는 턱이 없어 휠체어 접근 가능함", "대여가능"))
				.containsExactly("WHEELCHAIR", "STROLLER");
	}

	@Test
	@DisplayName("빈 칸과 없는 값에는 아무것도 안 붙는다 — 행이 없는 것은 접근 불가가 아니라 모른다다")
	void emptyMeansUnknownNotInaccessible() {
		assertThat(BarrierFreeAccessibility.of("", "")).isEmpty();
		assertThat(BarrierFreeAccessibility.of(null, null)).isEmpty();
		assertThat(BarrierFreeAccessibility.of("   ", "   ")).isEmpty();
	}

	@Test
	@DisplayName("무거운 짐은 어떤 문장으로도 안 나온다 — 무장애 자료에 그 칸이 없다")
	void heavyLuggageIsNeverProduced() {
		String[] sentences = { "엘리베이터 있음", "주출입구는 턱이 없어 휠체어 접근 가능함", "대여가능", "" };
		for (String exit : sentences) {
			for (String stroller : sentences) {
				assertThat(BarrierFreeAccessibility.of(exit, stroller)).doesNotContain("HEAVY_LUGGAGE");
			}
		}
	}
}
