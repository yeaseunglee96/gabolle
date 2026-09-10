package com.gabolle.backend.assistant.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.assistant.config.AssistantProperties;
import com.gabolle.backend.assistant.domain.PlanPatch;

/**
 * {@link ClaudeAssistantAdapter#buildPlanSummary} 검증 — S15P21E201-802.
 *
 * <p>🔴 이 메서드가 곧 "장소 이름이 새어나갈 통로가 없다"는 보장의 실체다 — {@code plan}
 * 응답의 reply/summary 는 모델이 쓴 문장이 아니라 여기서 patch 값만 보고 조립된다. 그래서
 * 이 테스트는 모델을 흉내 낼 필요가 없다 — 순수 함수다.
 */
class ClaudeAssistantAdapterTest {

	private final ClaudeAssistantAdapter adapter = new ClaudeAssistantAdapter(new AssistantProperties());

	@Test
	@DisplayName("지역·인원이 채워지면 그 값만 한국어 문장으로 나열한다")
	void summarizesFilledFieldsOnly() {
		PlanPatch patch = new PlanPatch(null, null, 2, null, null, List.of("해운대", "광안리"), List.of("맛집"), null,
				null, null);

		List<String> summary = this.adapter.buildPlanSummary(patch);

		assertThat(summary).containsExactly("지역: 해운대, 광안리", "인원: 2명", "음식: 맛집");
	}

	@Test
	@DisplayName("아무 것도 언급되지 않으면 빈 목록이다")
	void emptyPatchProducesEmptySummary() {
		PlanPatch patch = new PlanPatch(null, null, null, null, null, null, null, null, null, null);

		assertThat(this.adapter.buildPlanSummary(patch)).isEmpty();
	}

	@Test
	@DisplayName("이동수단 코드는 한국어 라벨로 바뀐다")
	void transportCodeIsTranslatedToKoreanLabel() {
		PlanPatch patch = new PlanPatch(null, null, null, null, null, null, null, null, "TRANSIT", null);

		assertThat(this.adapter.buildPlanSummary(patch)).containsExactly("이동수단: 대중교통");
	}

	@Test
	@DisplayName("travelers 가 없고 adults/children 만 있으면 그걸로 인원을 조립한다")
	void fallsBackToAdultsAndChildrenWhenTravelersIsMissing() {
		PlanPatch patch = new PlanPatch(null, null, null, 2, 1, null, null, null, null, null);

		assertThat(this.adapter.buildPlanSummary(patch)).containsExactly("인원: 성인 2명, 아동 1명");
	}
}
