package com.gabolle.backend.recommendation.adapter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.gabolle.backend.trip.domain.TripSeedPlace;

/**
 * 복제 씨앗 우대. 공유 일정을 복제한 여행에는 원본의 장소 구성이 {@code trip_seed_place} 에 남아 있고,
 * 후보 점수에서 그 장소들을 앞세운다.
 *
 * 점수만 올리고 판정은 건드리지 않는다 — 씨앗이라도 제약에 걸리면 {@code constraintVerdict} 가 FAIL 이고
 * {@code CandidateAssembler} 가 뺀다. {@code preRankScore} 가 {@code null} 인 후보도 그대로 둔다.
 * {@link #BOOST} 가 1.0 인 이유는 기본 점수가 가중치 합 1.0 인 0~1 스케일이라, 1.0 을 더하면 씨앗이 씨앗
 * 아닌 어떤 후보보다 앞서면서 씨앗들 사이의 순서는 원래 점수가 정하기 때문이다.
 *
 * 후보 풀에 없는 씨앗(출발지 반경 밖·취향 카테고리 밖)은 여기서 끼워 넣지 않는다 — 알려진 한계다.
 */
public final class SeedBoost {

	public static final double BOOST = 1.0;

	public static final String REASON_CODE = "SEED_FROM_SHARED_ITINERARY";

	private SeedBoost() {
	}

	public static List<EngineCandidate> apply(List<EngineCandidate> candidates, List<TripSeedPlace> seeds) {
		if (seeds == null || seeds.isEmpty()) {
			return candidates;
		}
		Map<UUID, Integer> sequenceByPlace = seeds.stream()
				.collect(Collectors.toMap(s -> UUID.fromString(s.placeId()), TripSeedPlace::sequence, (a, b) -> a));

		List<EngineCandidate> boosted = new ArrayList<>(candidates.size());
		for (EngineCandidate candidate : candidates) {
			Integer sequence = sequenceByPlace.get(candidate.placeId());
			boosted.add(sequence == null ? candidate : boost(candidate, sequence));
		}
		return boosted;
	}

	private static EngineCandidate boost(EngineCandidate c, int sequence) {
		Double score = (c.preRankScore() == null) ? null : c.preRankScore() + BOOST;

		Map<String, Object> components = new LinkedHashMap<>(c.scoreComponents());
		components.put("seed", Map.of("boost", BOOST, "sourceSequence", sequence));

		List<String> reasons = new ArrayList<>(c.reasonCodes());
		reasons.add(REASON_CODE);

		return new EngineCandidate(c.placeId(), c.candidateSource(), c.constraintVerdict(), c.violations(),
				c.unknownFacts(), c.constraintConfidence(), c.featureValues(), components, score, reasons,
				c.warningCodes());
	}
}
