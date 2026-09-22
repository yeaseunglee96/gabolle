package com.gabolle.backend.preference;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.preference.domain.TasteDimension;
import com.gabolle.backend.preference.domain.TasteEvidence;
import com.gabolle.backend.preference.domain.TasteWeightComponent;
import com.gabolle.backend.preference.domain.UserTasteWeight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 나눠 적은 행을 읽을 때 하나로 합치는 규칙 (S15P21E201-1499).
 *
 * <p>이 클래스가 지키는 약속은 하나다 — <b>나눠 적어도 합친 값이 예전과 같다.</b> 그게 성립하는
 * 이유는 합치는 규칙이 원래 «합» 이었기 때문이고, 평균이었다면 이 변경 자체가 불가능했다.
 */
class TasteWeightComponentTest {

	private static final UUID VECTOR = UUID.randomUUID();

	private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-09-22T12:00:00Z");

	@Test
	@DisplayName("설문 행과 행동 행이 한 성분으로 합쳐진다 — 무게는 합, 근거는 BLENDED")
	void mergesSurveyAndInteractionIntoOneComponent() {
		List<TasteWeightComponent> merged = TasteWeightComponent.merge(List.of(
				UserTasteWeight.fromSurvey(VECTOR, TasteDimension.CATEGORY, "CAFE", 0.4, NOW),
				UserTasteWeight.fromInteraction(VECTOR, TasteDimension.CATEGORY, "CAFE", 0.6, 3, NOW)));

		assertThat(merged).hasSize(1);
		TasteWeightComponent cafe = merged.get(0);
		assertThat(cafe.weight()).isCloseTo(1.0, within(1e-9));
		assertThat(cafe.evidence()).isEqualTo(TasteEvidence.BLENDED);
		// 설문 행의 뒷받침은 0 이라, 더해도 행동 행의 관측 수 그대로다.
		assertThat(cafe.support()).isEqualTo(3);
	}

	@Test
	@DisplayName("🔴 합이 범위를 넘으면 자른다 — 「좋아한다」보다 더 좋아할 수는 없다")
	void clampsSumToRange() {
		List<TasteWeightComponent> merged = TasteWeightComponent.merge(List.of(
				UserTasteWeight.fromSurvey(VECTOR, TasteDimension.CATEGORY, "CAFE", 1.0, NOW),
				UserTasteWeight.fromInteraction(VECTOR, TasteDimension.CATEGORY, "CAFE", 0.6, 2, NOW)));

		assertThat(merged.get(0).weight()).isCloseTo(1.0, within(1e-9));
	}

	@Test
	@DisplayName("설문 행만 있으면 근거도 설문 그대로 — 채점기가 이것을 걸러야 한다")
	void keepsSurveyOnlyEvidence() {
		List<TasteWeightComponent> merged = TasteWeightComponent
				.merge(List.of(UserTasteWeight.fromSurvey(VECTOR, TasteDimension.CATEGORY, "CAFE", 0.5, NOW)));

		assertThat(merged.get(0).evidence()).isEqualTo(TasteEvidence.SURVEY);
		assertThat(merged.get(0).weight()).isCloseTo(0.5, within(1e-9));
	}

	@Test
	@DisplayName("행동 행만 있으면 근거도 행동 그대로")
	void keepsInteractionOnlyEvidence() {
		List<TasteWeightComponent> merged = TasteWeightComponent.merge(
				List.of(UserTasteWeight.fromInteraction(VECTOR, TasteDimension.CATEGORY, "CAFE", -0.3, 5, NOW)));

		assertThat(merged.get(0).evidence()).isEqualTo(TasteEvidence.INTERACTION);
		assertThat(merged.get(0).support()).isEqualTo(5);
	}

	/**
	 * 🔴 이 갈래가 조용히 틀리면 옛 성분이 <b>채점에서 통째로 빠진다</b> — 채점기가
	 * {@code SURVEY} 를 거르므로, 근거를 잘못 계산해 {@code SURVEY} 로 내면 그 성분은 없는 것이
	 * 된다. 그래서 값이 아니라 근거를 못 박는다.
	 */
	@Test
	@DisplayName("🔴 예전에 적힌 BLENDED 행 하나는 그대로 한 성분이다")
	void treatsLegacyBlendedRowAsOneComponent() {
		List<TasteWeightComponent> merged = TasteWeightComponent.merge(
				List.of(UserTasteWeight.legacyBlended(VECTOR, TasteDimension.CATEGORY, "CAFE", 0.8, 4, NOW)));

		assertThat(merged).hasSize(1);
		assertThat(merged.get(0).evidence()).isEqualTo(TasteEvidence.BLENDED);
		assertThat(merged.get(0).weight()).isCloseTo(0.8, within(1e-9));
	}

	@Test
	@DisplayName("차원이나 코드가 다르면 서로 안 섞인다")
	void keepsDifferentComponentsApart() {
		List<TasteWeightComponent> merged = TasteWeightComponent.merge(List.of(
				UserTasteWeight.fromSurvey(VECTOR, TasteDimension.CATEGORY, "CAFE", 0.4, NOW),
				UserTasteWeight.fromSurvey(VECTOR, TasteDimension.CATEGORY, "SEA_BEACH", 0.5, NOW),
				UserTasteWeight.fromSurvey(VECTOR, TasteDimension.ATMOSPHERE, "CAFE", 0.6, NOW)));

		assertThat(merged).hasSize(3);
		assertThat(merged).extracting(TasteWeightComponent::weight)
			.containsExactly(0.4, 0.5, 0.6);
	}

	@Test
	@DisplayName("빈 목록과 null 은 빈 목록 — 아직 접힌 적 없는 사용자가 여기로 온다")
	void emptyInputGivesEmptyOutput() {
		assertThat(TasteWeightComponent.merge(List.of())).isEmpty();
		assertThat(TasteWeightComponent.merge(null)).isEmpty();
	}

}
