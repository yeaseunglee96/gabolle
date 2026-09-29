package com.gabolle.backend.place.loader;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 검사에 쓰는 문장은 실제 수집본에서 그대로 옮긴 것이다. */
class BarrierFreeAccessibilityTest {

	@Test
	@DisplayName("원천이 휠체어 접근 가능이라고 쓴 문장에만 붙인다")
	void wheelchairOnlyWhenTheSourceSaysSo() {
		assertThat(BarrierFreeAccessibility.of("주출입구는 턱이 없어 휠체어 접근 가능함_무장애 편의시설", "", ""))
				.containsExactly("WHEELCHAIR");
		assertThat(BarrierFreeAccessibility.of("주출입구는 경사로가 있어 휠체어 접근 가능함", "", ""))
				.containsExactly("WHEELCHAIR");
	}

	@Test
	@DisplayName("문 모양만 적힌 것은 접근성 이야기가 아니다")
	void doorShapeIsNotAnAccessibilityClaim() {
		assertThat(BarrierFreeAccessibility.of("여닫이문", "", "")).isEmpty();
		assertThat(BarrierFreeAccessibility.of("자동문", "", "")).isEmpty();
	}

	@Test
	@DisplayName("주출입구에 계단이 있다고 적힌 곳은 안 붙는다 — 이 한 줄이 판정을 좁게 만든 이유다")
	void mainEntranceWithStairsGetsNothing() {
		assertThat(BarrierFreeAccessibility.of("주출입구는 계단이 있고 보조출입구는 무단차임", "", ""))
				.as("보조출입구가 무단차라는 말에 이끌려 붙이면 사람을 계단 앞에 보낸다")
				.isEmpty();
	}

	@Test
	@DisplayName("뜻이 같아도 문구가 다르면 안 붙는다 — 모름으로 남는 손해가 더 작다")
	void sameMeaningDifferentWordsIsLeftUnknown() {
		assertThat(BarrierFreeAccessibility.of("높낮이 차가 없어 휠체어를 이용하여 출입가능", "", "")).isEmpty();
		assertThat(BarrierFreeAccessibility.of("주출입구에 경사로 설치되어 있음", "", "")).isEmpty();
	}

	@Test
	@DisplayName("🔴 경로 칸에만 그 문구가 있어도 붙인다 — 원천이 나눠 적었을 뿐이다")
	void routeAloneIsEnough() {
		assertThat(BarrierFreeAccessibility.of("", "무단차 거리로 휠체어 접근 가능함", ""))
				.containsExactly("WHEELCHAIR");
		assertThat(BarrierFreeAccessibility.of("", "출입구까지 단차가 없어 휠체어 접근 가능함", ""))
				.containsExactly("WHEELCHAIR");
	}

	@Test
	@DisplayName("출입구와 경로가 둘 다 말해도 코드는 하나다")
	void bothColumnsStillOneCode() {
		assertThat(BarrierFreeAccessibility.of("주출입구는 휠체어 접근 가능함", "휠체어 접근 가능", ""))
				.containsExactly("WHEELCHAIR");
	}

	@Test
	@DisplayName("🔴 경로 칸이라고 문구가 느슨해지지는 않는다")
	void routeUsesTheSameStrictPhrase() {
		assertThat(BarrierFreeAccessibility.of("", "출입통로가 넓어 휠체어, 전동 스쿠터 진입 쉬움. 낮은 턱 있음.", ""))
				.as("턱이 있다고 적힌 곳이다").isEmpty();
		assertThat(BarrierFreeAccessibility.of("", "경사도 10도 내외의 경사구간 있음, 휠체어 사용자 등 중증장애인 주의", ""))
				.as("경고문이지 접근 가능하다는 말이 아니다").isEmpty();
	}
	/**
	 * 🔴 예전에는 「유모차 칸은 값이 있으면 붙인다」였다. 그 칸은 유아차 <b>대여</b> 안내라(부산 수집본 값 15개가 전부 대여
	 * 문구) 「유아차로 들어갈 수 있다」는 뜻이 아니다. 확인된 표식은 경사 추정보다 앞서므로, 잘못 붙이면 가파른 곳도
	 * 「확인됨」으로 통과한다.
	 */
	@Test
	@DisplayName("🔴 유모차 칸의 대여 안내로는 STROLLER 를 안 붙인다 — 빌려준다는 것은 들어갈 수 있다는 말이 아니다")
	void strollerRentalIsNotAccess() {
		// 수집본에 실제로 있는 값들이다.
		for (String rental : List.of("대여가능", "유모차 있음", "유모차 무료 대여(2대,1층안내데스크,신분증보관)",
				"대여 가능(10대/안내소)", "유모차 대여 가능함", "가능")) {
			assertThat(BarrierFreeAccessibility.of("", "", rental)).as(rental).isEmpty();
		}
	}

	@Test
	@DisplayName("휠체어 문구와 유모차 대여가 같이 있으면 휠체어만 붙는다")
	void wheelchairStillAppliesNextToStrollerRental() {
		assertThat(BarrierFreeAccessibility.of("주출입구는 턱이 없어 휠체어 접근 가능함", "", "대여가능"))
				.containsExactly("WHEELCHAIR");
	}

	@Test
	@DisplayName("빈 칸과 없는 값에는 아무것도 안 붙는다 — 행이 없는 것은 접근 불가가 아니라 모른다다")
	void emptyMeansUnknownNotInaccessible() {
		assertThat(BarrierFreeAccessibility.of("", "", "")).isEmpty();
		assertThat(BarrierFreeAccessibility.of(null, "", null)).isEmpty();
		assertThat(BarrierFreeAccessibility.of("   ", "", "   ")).isEmpty();
	}

	@Test
	@DisplayName("무거운 짐은 어떤 문장으로도 안 나온다 — 무장애 자료에 그 칸이 없다")
	void heavyLuggageIsNeverProduced() {
		String[] sentences = { "엘리베이터 있음", "주출입구는 턱이 없어 휠체어 접근 가능함", "대여가능", "" };
		for (String exit : sentences) {
			for (String stroller : sentences) {
				assertThat(BarrierFreeAccessibility.of(exit, "", stroller)).doesNotContain("HEAVY_LUGGAGE");
			}
		}
	}
}
