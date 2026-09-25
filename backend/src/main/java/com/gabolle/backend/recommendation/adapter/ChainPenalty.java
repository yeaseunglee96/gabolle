package com.gabolle.backend.recommendation.adapter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.gabolle.backend.place.api.PlaceCandidateResponse;
import com.gabolle.backend.place.service.ChainBrand;

/**
 * 전국 프랜차이즈는 추천 점수를 낮춘다 — 동네 가게가 먼저 나오게 (S15P21E201-1616, 사용자 결정).
 *
 * <p>{@link BudgetFit} 처럼 <b>점수만 움직이고 후보를 빼지 않는다.</b> 체인밖에 없는 동네에서도 일정은 채워진다.
 * 판정은 {@link ChainBrand} 한 곳이다 — 일정에 같은 상표를 한 번만 넣는 쪽도 같은 사전을 쓴다.
 *
 * <p>화면에 나가는 이유 코드(reasonCodes)는 더하지 않는다 — 앱이 모르는 코드를 받으면 그대로 그리게 된다.
 * 무엇을 얼마나 뺐는지는 점수 구성({@code scoreComponents.chain})에 남는다.
 */
final class ChainPenalty {

	/**
	 * 체인 한 곳에서 빼는 점수. 예산 맞춤의 가산({@link BudgetFit#MATCH_BONUS})과 같은 크기다 — 비슷한 점수의 동네
	 * 가게가 앞서게 할 만큼이고, 훨씬 잘 맞는 체인까지 밀어내지는 않는다. 🔴 실측이 아니라 정한 값이다.
	 */
	static final double PENALTY = 0.10;

	private ChainPenalty() {
	}

	/** 후보마다의 상표. 사전에 없는 곳은 열쇠를 안 만든다. */
	static Map<UUID, String> brandsOf(PlaceCandidateResponse response) {
		Map<UUID, String> brandByPlace = new LinkedHashMap<>();
		for (PlaceCandidateResponse.Candidate candidate : response.candidates()) {
			String brand = ChainBrand.brandOf(candidate.nameKo());
			if (brand != null) {
				brandByPlace.put(candidate.placeId(), brand);
			}
		}
		return brandByPlace;
	}

	static List<EngineCandidate> apply(List<EngineCandidate> candidates, Map<UUID, String> brandByPlace) {
		if (brandByPlace == null || brandByPlace.isEmpty()) {
			return candidates;
		}
		List<EngineCandidate> adjusted = new ArrayList<>(candidates.size());
		for (EngineCandidate candidate : candidates) {
			String brand = brandByPlace.get(candidate.placeId());
			adjusted.add((brand == null) ? candidate : lower(candidate, brand));
		}
		return adjusted;
	}

	private static EngineCandidate lower(EngineCandidate c, String brand) {
		// 점수가 없는 후보는 0 으로 치지 않는다 — 없는 것과 낮은 것은 다르다(BudgetFit 과 같다).
		Double score = (c.preRankScore() == null) ? null : c.preRankScore() - PENALTY;
		Map<String, Object> components = new LinkedHashMap<>(c.scoreComponents());
		components.put("chain", Map.of("delta", -PENALTY, "brand", brand));
		return new EngineCandidate(c.placeId(), c.candidateSource(), c.constraintVerdict(), c.violations(),
				c.unknownFacts(), c.constraintConfidence(), c.featureValues(), components, score, c.reasonCodes(),
				c.warningCodes());
	}
}
