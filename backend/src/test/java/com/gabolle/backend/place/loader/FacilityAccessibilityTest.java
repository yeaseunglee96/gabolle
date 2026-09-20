package com.gabolle.backend.place.loader;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 실태조사 항목 → 접근성 표식 — S15P21E201-1365.
 *
 * <p>{@link BarrierFreeAccessibilityTest} 와 같은 이유로 좁게 잡는다 — 이 판정이 느슨하면
 * 휠체어를 쓰는 사람이 <b>문 앞에서 돌아선다.</b>
 *
 * <p>항목 이름은 2026-09-21 실태조사 응답에서 그대로 옮겼다.
 *
 * <p>🔴 <b>표본을 넓힐 때마다 항목 종류가 늘었다</b> — 33곳에서 5종, 45곳에서 6종, 88곳에서
 * 11종이었다. 그러니 "다 봤다" 고 말할 수 없다. 이 시험은 88곳에서 나온 11종을 다루되,
 * <b>모르는 이름은 안 붙이는 쪽</b>으로 떨어지게 두는 것이 설계다.
 */
class FacilityAccessibilityTest {

	@Test
	@DisplayName("접근로가 있으면 붙인다 — 그 이름이 되려면 폭 1.2m·기울기 1/12 을 만족해야 한다")
	void routeMeansTheLawsStandardWasMet() {
		assertThat(FacilityAccessibility.of("주출입구(문), 주출입구 접근로"))
				.containsExactly("WHEELCHAIR");
	}

	@Test
	@DisplayName("높이차이 제거만 있어도 붙인다 — 턱이 없다는 말이다")
	void noStepAloneIsEnough() {
		assertThat(FacilityAccessibility.of("주출입구 높이차이 제거, 주출입구(문)"))
				.containsExactly("WHEELCHAIR");
	}

	@Test
	@DisplayName("🔴 문만 있으면 안 붙인다 — 실측에서 문 41곳 대 접근로 34곳, 그 차이 7곳이 여기 든다")
	void aDoorAloneSaysNothingAboutGettingThere() {
		assertThat(FacilityAccessibility.of("주출입구(문)"))
				.as("문이 조사됐다는 것과 거기까지 갈 수 있다는 것은 다른 말이다")
				.isEmpty();
	}

	@Test
	@DisplayName("🔴 같은 항목의 공백 없는 변형도 걸린다 — 「주출입구높이차이제거(경사로)」 3곳")
	void spacingVariantsAreTheSameItem() {
		assertThat(FacilityAccessibility.of("주출입구높이차이제거(경사로)"))
				.as("정확히 맞춰 보면 이 3곳을 놓치고, 놓친 것은 「근거 없음」으로 세어져 아무 오류도 안 낸다")
				.containsExactly("WHEELCHAIR");
		assertThat(FacilityAccessibility.of("주출입구접근로")).containsExactly("WHEELCHAIR");
	}

	@Test
	@DisplayName("공백을 지워도 「주출입문」·「주출입구(문)」은 여전히 안 걸린다 — 좁은 쪽이 안 흔들려야 한다")
	void strippingSpacesMustNotWidenTheRule() {
		assertThat(FacilityAccessibility.of("주출입문")).isEmpty();
		assertThat(FacilityAccessibility.of("주출입구(문), 주출입문, 안내설비")).isEmpty();
	}

	@Test
	@DisplayName("쉼표는 안 지운다 — 지우면 앞 항목 끝과 뒤 항목 앞이 붙어 없던 이름이 생긴다")
	void commasKeepItemsApart() {
		assertThat(FacilityAccessibility.of("주출입구, 접근로 안내"))
				.as("「주출입구」와 「접근로 안내」는 서로 다른 항목이다. 붙이면 「주출입구접근로」가 된다")
				.isEmpty();
	}

	@Test
	@DisplayName("화장실·승강기·주차구역은 이동 조건이 아니다 — 앱에 대응하는 코드가 없다")
	void otherFacilitiesAreNotMovementConditions() {
		assertThat(FacilityAccessibility.of("장애인사용가능화장실")).isEmpty();
		assertThat(FacilityAccessibility.of("승강기")).isEmpty();
		assertThat(FacilityAccessibility.of("장애인전용주차구역")).isEmpty();
		assertThat(FacilityAccessibility.of("장애인전용주차구역, 승강기, 장애인사용가능화장실"))
				.as("셋이 다 있어도 주출입구 이야기가 없으면 들어갈 수 있는지 모른다")
				.isEmpty();
	}

	@Test
	@DisplayName("항목이 비어 있으면 안 붙인다 — 실측 47곳 중 2곳이 비어 있었다")
	void emptyMeansNothingWasInstalled() {
		assertThat(FacilityAccessibility.of(null)).isEmpty();
		assertThat(FacilityAccessibility.of("")).isEmpty();
		assertThat(FacilityAccessibility.of("   ")).isEmpty();
	}

	@Test
	@DisplayName("여러 항목이 섞여 있어도 근거가 하나 있으면 붙고, 표식은 하나만 나온다")
	void oneTagEvenWhenBothReasonsArePresent() {
		assertThat(FacilityAccessibility.of(
				"주출입구(문), 주출입구 접근로, 주출입구 높이차이 제거, 장애인전용주차구역"))
				.as("접근로와 높이차이가 둘 다 있어도 붙는 표식은 WHEELCHAIR 하나다")
				.containsExactly("WHEELCHAIR");
	}
}
