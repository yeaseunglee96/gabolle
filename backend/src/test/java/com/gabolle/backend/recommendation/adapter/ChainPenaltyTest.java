package com.gabolle.backend.recommendation.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.place.api.PlaceCandidateResponse;
import com.gabolle.backend.recommendation.domain.ConstraintVerdict;

/** 전국 프랜차이즈는 추천 점수를 낮춘다 — 동네 가게가 먼저 나오게 (S15P21E201-1616). */
class ChainPenaltyTest {

	private static final UUID CHAIN = new UUID(7L, 1L);

	private static final UUID LOCAL = new UUID(7L, 2L);

	@Test
	@DisplayName("🔴 점수가 같으면 동네 가게가 체인보다 앞선다 — 이름에서 상표를 찾아 체인만 낮춘다")
	void aLocalShopGoesAheadOfAChainWithTheSameScore() {
		PlaceCandidateResponse response = new PlaceCandidateResponse(List.of(
				candidate(CHAIN, "배스킨라빈스광안역점"), candidate(LOCAL, "쌍둥이돼지국밥 본점")),
				2, 0, false, List.of(), List.of(), false, List.of());

		List<EngineCandidate> out = ChainPenalty.apply(
				List.of(scored(CHAIN, 0.60), scored(LOCAL, 0.60)), ChainPenalty.brandsOf(response));

		assertThat(out.get(0).preRankScore()).isEqualTo(0.60 - ChainPenalty.PENALTY);
		assertThat(out.get(1).preRankScore()).isEqualTo(0.60);
		assertThat(out.get(0).scoreComponents()).containsKey("chain");
	}

	@Test
	@DisplayName("후보를 빼지 않고, 화면에 나가는 이유 코드도 안 건드린다")
	void nothingIsDroppedAndReasonCodesStay() {
		List<EngineCandidate> out = ChainPenalty.apply(List.of(scored(CHAIN, 0.60)), Map.of(CHAIN, "배스킨라빈스"));

		assertThat(out).hasSize(1);
		assertThat(out.get(0).reasonCodes()).containsExactly("REASON");
	}

	private static PlaceCandidateResponse.Candidate candidate(UUID placeId, String name) {
		return new PlaceCandidateResponse.Candidate(placeId, name, "FOOD", 35.15, 129.05, 100L, List.of());
	}

	private static EngineCandidate scored(UUID placeId, double score) {
		return new EngineCandidate(placeId, "TEST", ConstraintVerdict.PASS, List.of(), List.of(), null, Map.of(),
				Map.of(), score, List.of("REASON"), List.of());
	}
}
